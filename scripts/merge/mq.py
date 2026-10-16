#!/usr/bin/env python3
"""Merge queue, step 1 of the self-built queue: pull requests are brought up to date, tested by the full CI and merged, in two lanes.

  mq.py run                       runner: a code lane and a docs lane, each takes the first pull request of its lane from the queue, processes it and removes it; waits when its lane is empty
  mq.py once <pr>                 process one pull request (no queue)
  mq.py enqueue <pr>...           append (a number already queued is not added twice; one being processed is reported)
  mq.py remove <pr>...            take out
  mq.py before <pr> <other>       put <pr> in front of <other> (appends it when it is not queued)
  mq.py list                      the queue, one number per line
  mq.py status                    queue, lanes, pause state
  mq.py resume                    acknowledge the red main runs that paused the queue (coordinator only) and let both lanes go on
  mq.py train <merge_train.py args>   scripts/queue/merge_train.py: `train docs --branch B <session branch>@<sha>...` collects documentation commits (one by one, docs/ only, never an octopus merge)
  mq.py --self-test

Lanes. docs: every changed path is pure documentation (scripts/ci/relevant_change.py tier=docs: nothing a build or test reads). code: everything else, also the middle tier (docs a container-free test reads).
With branch protection's "branch must be up to date" off, GitHub no longer blocks a merge of a branch behind main; the queue does it itself: the code lane merges only a head that contains the current main and whose full
CI ran on that head, the docs lane syncs main and waits for the light run before merging. Merges are serialized by a lock, so two lanes never merge on a stale main: the one that comes second sees it is behind,
updates and goes through CI again.

Per pull request: cancel the CI runs of the other queued pull requests (they are re-run after their own update; until then they only take runners), bring the branch up to date (gh update-branch; a pull request that
touches .github/workflows is updated by a local merge pushed over ssh, because the gh token lacks the workflow scope), label it `queued` (heavy CI steps run only with the label, scripts/ci/heavy_gate.py), wait for the
checks while cancelling the others' runs, rerun failed checks once, then merge with a merge commit, pinned to the head that was checked (`--match-head-commit`). Merge only when `gh pr checks` lists the three
required checks (backend verify, frontend verify, secret scan, none of them with the " (light)" suffix) and every one of them is `pass`; "no failure so far" is never enough (a head still running is not green).
Outcomes: MERGED, CONFLICT (text conflict with main: back to the author), HOLD (red after the rerun, CI timeout, closed, an update that could not be pushed, attempts exhausted: reported to the coordinator).

Pause. Before taking a pull request, and again right before a merge, the queue looks at main's latest push run and its latest nightly (schedule) run of the ci workflow. A red one pauses both lanes (PAUSED in the log,
file <queue dir>/mq.paused) until `mq.py resume`; the resume acknowledges the red runs it saw, so the same red run does not pause again, a new red run does. A push to main runs the light steps and every container-free
unit test (ci.yml), the nightly the full ci: together they are the alarm for a merge whose tree no full run tested exactly as merged.

Queue file: one pull request number per line, anything else is kept as it is. Every change goes through flock on <queue>.lock and an atomic replace (temporary file, then rename), so an enqueue can no longer be
lost to the runner removing a finished entry (a raw `echo n >> merge.queue` or `sed -i` outside this script is not safe). Log: one line per event,
"<ISO time> <EVENT> pr=#<n> run=<ci run id or -> elapsed=<seconds since the pull request was taken>s <detail>", appended to <queue dir>/merge.log and echoed. A failing runner step is logged as ERROR and retried
with a growing pause; the third failure in a row puts the pull request on HOLD and takes it out of the queue.
"""
import argparse
import contextlib
import datetime
import fcntl
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
REQUIRED = ("backend verify", "frontend verify", "secret scan")
OK = ("pass", "skipping")
LINE = re.compile(r"^\s*(\d+)\s*$")
LANES = ("code", "docs")
FAILING = ("failure", "timed_out", "startup_failure")


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
        self.lock = threading.Lock()

    def event(self, ev, pr=None, run=None, since=None, detail=""):
        started = since if since is not None else time.monotonic()
        line = f"{self.now().isoformat(timespec='seconds')} {ev} pr=#{pr if pr is not None else '-'} run={run if run else '-'} elapsed={max(0, int(time.monotonic() - started))}s"
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

    def enqueue(self, *prs):
        added = []
        with self.locked():
            lines = self._read()
            for n in prs:
                if not any((m := LINE.match(l)) and int(m.group(1)) == n for l in lines):
                    lines.append(str(n))
                    added.append(n)
            self._write(lines)
        return added

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
    """The GitHub side; the self-test replaces it. A call that fails returns None (or False), never a value that looks like a result."""

    def view(self, n):
        r = sh(["gh", "pr", "view", str(n), "--json", "state,mergeStateStatus,labels,headRefName,headRefOid"])
        try:
            d = json.loads(r.stdout)
            return {"state": d["state"], "merge": d["mergeStateStatus"], "labels": [l["name"] for l in d["labels"]], "branch": d["headRefName"], "head": d["headRefOid"]}
        except (ValueError, KeyError):
            return None

    def behind_by(self, n, head):
        r = sh(["gh", "api", f"repos/{{owner}}/{{repo}}/compare/main...{head}", "--jq", ".behind_by"])
        return int(r.stdout.strip()) if r.returncode == 0 and r.stdout.strip().isdigit() else None

    def files(self, n):
        r = sh(["gh", "api", "--paginate", f"repos/{{owner}}/{{repo}}/pulls/{n}/files", "--jq", ".[] | .filename, .previous_filename"])
        return [x for x in r.stdout.splitlines() if x and x != "null"] if r.returncode == 0 else None

    def checks(self, n):
        r = sh(["gh", "pr", "checks", str(n), "--json", "name,bucket,link"])
        try:
            return json.loads(r.stdout)
        except ValueError:
            return None

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

    def main_run(self, event):
        """Latest ci run of main for `event` (push | schedule): {'id', 'status', 'conclusion', 'sha'} or None."""
        args = ["gh", "run", "list", "--workflow", "ci", "--event", event, "--limit", "1", "--json", "databaseId,status,conclusion,headSha,headBranch"]
        if event == "push":
            args += ["--branch", "main"]
        r = sh(args)
        try:
            row = json.loads(r.stdout)[0]
        except (ValueError, IndexError):
            return None
        return {"id": row["databaseId"], "status": row["status"], "conclusion": row["conclusion"], "sha": row["headSha"]} if row.get("headBranch", "main") == "main" else None

    def merge(self, n, head):
        r = sh(["gh", "pr", "merge", str(n), "--merge", "--match-head-commit", head])
        return r.returncode == 0, (r.stderr or r.stdout).strip()


def update_over_ssh(repo, branch, wt):
    """Local merge of origin/main into the pull request branch, pushed over ssh (git, not the gh token): 'ok' | 'conflict' | 'error'."""
    if sh(["git", "-C", repo, "fetch", "-q", "origin"]).returncode:
        return "error"
    sh(["git", "-C", repo, "worktree", "remove", "-f", wt])
    sh(["git", "-C", repo, "worktree", "prune"])
    if os.path.exists(wt):   # a plain directory an earlier, killed run left behind; only our own scratch directory is ever removed
        if not os.path.basename(wt).startswith("mq-wt"):
            return "error"
        shutil.rmtree(wt, ignore_errors=True)
    if sh(["git", "-C", repo, "worktree", "add", "-q", "--detach", wt, f"origin/{branch}"]).returncode:
        return "error"
    try:
        if sh(["git", "-C", wt, "merge", "-q", "--no-edit", "origin/main"]).returncode:
            conflicted = sh(["git", "-C", wt, "diff", "--name-only", "--diff-filter=U"]).stdout.strip()
            sh(["git", "-C", wt, "merge", "--abort"])
            return "conflict" if conflicted else "error"
        return "ok" if sh(["git", "-C", wt, "push", "-q", "origin", f"HEAD:{branch}"]).returncode == 0 else "error"
    finally:
        sh(["git", "-C", repo, "worktree", "remove", "-f", wt])


def run_id(link):
    m = re.search(r"runs/(\d+)", link or "")
    return m.group(1) if m else None


def required_state(checks):
    """(ok, problems): the three required checks are listed (never with the " (light)" suffix) and every one of them is `pass`."""
    problems = []
    for prefix in REQUIRED:
        found = [c for c in checks if c["name"].startswith(prefix) and not c["name"].endswith("(light)")]
        if not found:
            problems.append(f"{prefix}: not listed")
        problems += [f"{c['name']}={c['bucket']}" for c in found if c["bucket"] != "pass"]
    return not problems, problems


class Pause:
    """<queue dir>/mq.paused holds the reason; mq.acked the red run ids a resume has acknowledged."""

    def __init__(self, directory, gh, log):
        self.paused_file, self.acked_file, self.gh, self.log = os.path.join(directory, "mq.paused"), os.path.join(directory, "mq.acked"), gh, log
        self.lock = threading.Lock()

    def acked(self):
        try:
            return set(open(self.acked_file).read().split())
        except FileNotFoundError:
            return set()

    def red_runs(self):
        """[(kind, run id)]: main's latest push run and latest nightly run when they ended red."""
        out = []
        for kind, event in (("push", "push"), ("nightly", "schedule")):
            run = self.gh.main_run(event)
            if run and run["status"] == "completed" and run["conclusion"] in FAILING:
                out.append((kind, str(run["id"])))
        return out

    def is_paused(self):
        return os.path.exists(self.paused_file)

    def check(self):
        """True when the queue must not go on; pauses it when an unacknowledged red run is there."""
        with self.lock:
            if self.is_paused():
                return True
            fresh = [(kind, rid) for kind, rid in self.red_runs() if rid not in self.acked()]
            if not fresh:
                return False
            reason = ", ".join(f"main {kind} run {rid} is red" for kind, rid in fresh)
            with open(self.paused_file, "w", encoding="utf-8") as f:
                f.write(reason + "\n")
            self.log.event("PAUSED", None, fresh[0][1], None, reason + "; both lanes wait for `mq.py resume`")
            return True

    def resume(self):
        with self.lock:
            red = self.red_runs()
            with open(self.acked_file, "a", encoding="utf-8") as f:
                f.write("".join(rid + "\n" for _, rid in red))
            with contextlib.suppress(FileNotFoundError):
                os.unlink(self.paused_file)
            self.log.event("RESUMED", None, None, None, f"acknowledged red runs: {', '.join(rid for _, rid in red) or 'none'}")
            return red


def default_lane(repo):
    """'docs' when scripts/ci/relevant_change.py says tier=docs for every path of the pull request, else 'code'; a lookup that fails is 'code' (the stricter lane)."""
    def lane(gh, n):
        files = gh.files(n)
        if not files:
            return "code"
        with tempfile.NamedTemporaryFile("w", suffix=".paths", delete=False, encoding="utf-8") as f:
            f.write("\n".join(files) + "\n")
        try:
            r = sh([sys.executable, os.path.join(repo, "scripts/ci/relevant_change.py"), "--paths-from", f.name], cwd=repo)
        finally:
            os.unlink(f.name)
        return "docs" if r.returncode == 0 and "tier=docs" in r.stdout.splitlines() else "code"
    return lane


class Processor:
    def __init__(self, gh, queue, log, repo, sleep=time.sleep, updater=update_over_ssh, ci_timeout=90 * 60, wt=None, lane=None, pause=None):
        self.gh, self.queue, self.log, self.repo, self.sleep = gh, queue, log, repo, sleep
        self.updater, self.ci_timeout = updater, ci_timeout
        self.wt = wt or os.path.join(os.path.dirname(os.path.abspath(queue.path)), "mq-wt")
        self.lane_of = lane or default_lane(repo)
        self.pause = pause or Pause(os.path.dirname(os.path.abspath(queue.path)), gh, log)
        self.lanes, self.busy = {}, set()
        self.guard = threading.Lock()

    def lane(self, n, head=None):
        key = (n, head)
        if key not in self.lanes:
            self.lanes[key] = self.lane_of(self.gh, n)
        return self.lanes[key]

    def pick(self, lane):
        """First queued pull request of this lane that no worker has."""
        for n in self.queue.numbers():
            with self.guard:
                if n in self.busy:
                    continue
            v = self.gh.view(n)
            if self.lane(n, v["head"] if v else None) == lane:
                with self.guard:
                    if n in self.busy:
                        continue
                    self.busy.add(n)
                return n
        return None

    def cancel_others(self, head, since):
        for other in self.queue.numbers():
            with self.guard:
                if other == head or other in self.busy:   # a pull request the other lane is working on keeps its runs
                    continue
            v = self.gh.view(other)
            if not v:
                continue
            for run in self.gh.active_runs(v["branch"]):
                if self.gh.cancel_run(run):
                    self.log.event("CANCEL-RUN", other, run, since, f"waiting behind #{head}")

    def update(self, n, branch, since):
        if self.gh.update_branch(n):
            self.log.event("UPDATE", n, None, since, "gh update-branch")
            return "ok"
        res = self.updater(self.repo, branch, self.wt)
        self.log.event("SSH-UPDATE" if res == "ok" else f"SSH-UPDATE-{res.upper()}", n, None, since, "local merge pushed over ssh")
        return res

    def wait_checks(self, n, since):
        """Checks once none is pending; None when they stayed pending past the CI timeout. A failed gh call is no answer: asked again."""
        for _ in range(20):
            if self.gh.checks(n):
                break
            self.sleep(15)
        waited = 0
        while True:
            checks = self.gh.checks(n)
            if checks is not None and not any(c["bucket"] == "pending" for c in checks):
                return checks
            if waited >= self.ci_timeout:
                return None
            self.cancel_others(n, since)
            self.sleep(30)
            waited += 30

    def process(self, n):
        since = time.monotonic()
        self.log.event("START", n, None, since)
        try:
            return self._process(n, since)
        finally:
            with self.guard:
                self.busy.discard(n)

    def hold(self, n, run, since, why):
        self.log.event("HOLD", n, run, since, why)
        return "HOLD"

    def _process(self, n, since):
        self.cancel_others(n, since)
        attempt, moved, refused = 1, 0, 0   # attempts count red checks and a head that stopped being green; moved: main advanced during the full CI; refused: the merge itself was refused (own counter, growing pause)
        while attempt <= 4:
            v = self.gh.view(n)
            if v is None:
                return self.hold(n, None, since, "gh could not tell the state of the pull request")
            if v["state"] == "MERGED":
                self.log.event("MERGED", n, None, since, "already merged")
                return "MERGED"
            if v["state"] != "OPEN":
                return self.hold(n, None, since, f"the pull request is {v['state']}")
            if v["merge"] == "DIRTY":
                self.log.event("CONFLICT", n, None, since, "back to the author (text conflict)")
                return "CONFLICT"
            behind = self.gh.behind_by(n, v["head"])
            if v["merge"] == "BEHIND" or (behind or 0) > 0:
                res = self.update(n, v["branch"], since)
                if res != "ok":
                    if res == "conflict":
                        self.log.event("CONFLICT", n, None, since, "back to the author (text conflict with main)")
                        return "CONFLICT"
                    return self.hold(n, None, since, "the update could not be pushed")
                self.sleep(30)
                v = self.gh.view(n) or v
            if "queued" not in v["labels"]:
                if self.gh.label(n, "queued", True):
                    self.log.event("LABEL-QUEUED", n, None, since)
                self.sleep(20)
            checks = self.wait_checks(n, since)
            if checks is None:
                return self.hold(n, None, since, f"CI still pending after {self.ci_timeout // 60} minutes")
            if not checks:
                return self.hold(n, None, since, "gh pr checks lists no check at all")
            red = [c for c in checks if c["bucket"] not in OK]
            if red:
                runs = sorted({r for c in red if (r := run_id(c.get("link")))}, key=int)
                if (self.gh.view(n) or v)["merge"] == "BEHIND":
                    self.log.event("BEHIND-AFTER-FAIL", n, runs[0] if runs else None, since, "update and run again")
                    attempt += 1
                    continue
                if attempt == 1:
                    for r in runs:
                        self.gh.rerun(r)
                    self.log.event("RERUN-FAILED", n, runs[0] if runs else None, since, ", ".join(f"{c['name']}={c['bucket']}" for c in red))
                    self.sleep(30)
                    attempt += 1
                    continue
                return self.hold(n, ",".join(runs), since, "; ".join(f"{c['name']}={c['bucket']}" for c in red))
            ok, problems = required_state(checks)
            if not ok:
                self.log.event("REQUIRED-NOT-GREEN", n, None, since, "; ".join(problems) + "; relabel")
                self.gh.label(n, "queued", False)
                self.sleep(10)
                attempt += 1
                continue
            result = self.merge_when_fresh(n, since, checks)
            if result == "moved":
                moved += 1
                if moved > 6:
                    return self.hold(n, None, since, f"main moved {moved} times while the full CI of this pull request ran")
                continue
            if result == "changed":
                attempt += 1
                continue
            if result == "refused":
                refused += 1
                v = self.gh.view(n) or {}
                if refused >= 5:
                    return self.hold(n, None, since, f"merge refused {refused} times")
                if v.get("merge") != "BEHIND":
                    self.sleep(20 * 2 ** (refused - 1))
                continue
            return result
        return self.hold(n, None, since, "attempts exhausted")

    def merge_when_fresh(self, n, since, checks):
        """Under the merge lock: no red main run pauses the queue, the head is the one that was checked, it contains the current main, the checks are all green on it; then the merge, pinned to that head.
        Returns 'MERGED', 'moved' (behind main: update and test again), 'changed' (checks not green any more), 'refused' (gh refused the merge: logged with its message), or 'HOLD'."""
        with self.queue.merge_lock():
            while self.pause.check():
                self.log.event("WAIT-PAUSED", n, None, since, "main is red: waiting for `mq.py resume`")
                self.sleep(60)
            v = self.gh.view(n)
            if v is None or v["state"] != "OPEN":
                return self.hold(n, None, since, "the pull request changed state before the merge")
            head = v["head"]
            behind = self.gh.behind_by(n, head)
            if behind is None:
                return self.hold(n, None, since, "could not tell whether the head contains main")
            if behind > 0:
                self.log.event("BEHIND-AT-MERGE", n, None, since, f"main moved by {behind} commit(s) while the full CI ran: update and run again")
                return "moved"
            fresh = self.gh.checks(n)
            if not fresh or not required_state(fresh)[0] or any(c["bucket"] not in OK for c in fresh):
                self.log.event("CHECKS-CHANGED", n, None, since, "the checks are no longer all green on this head: run again")
                return "changed"
            ok, message = self.gh.merge(n, head)
            if ok:
                self.log.event("MERGED", n, run_id(checks[0].get("link")), since, head[:10])
                return "MERGED"
        v = self.gh.view(n) or {}
        names = [f"{c['name']}={c['bucket']}" for c in (self.gh.checks(n) or []) if c["bucket"] not in OK]
        self.log.event("RETRY-MERGE", n, None, since, f"refused: mergeState={v.get('merge')} failing={names or 'none'} gh: {message}")
        return "refused"


def run_lane(proc, lane, idle=60, stop=lambda: False):
    """One lane of the runner: pause check, pick, process, take out. A failing step is logged and retried with a growing pause; the third failure in a row puts the pull request on HOLD."""
    failures = {}
    while not stop():
        if proc.pause.check():
            proc.sleep(idle)
            continue
        n = proc.pick(lane)
        if n is None:
            proc.sleep(idle)
            continue
        try:
            try:
                result = proc.process(n)
            finally:
                with proc.guard:
                    proc.busy.discard(n)   # whatever happened, no worker holds it any more
        except (Exception, SystemExit) as e:
            failures[n] = failures.get(n, 0) + 1
            proc.log.event("ERROR", n, None, None, f"{type(e).__name__}: {e} (failure {failures[n]} in a row)")
            if failures[n] < 3:
                proc.sleep(30 * 2 ** failures[n])
                continue
            proc.log.event("HOLD", n, None, None, "the runner failed three times on this pull request")
            result = "HOLD"
        failures.pop(n, None)
        proc.queue.remove(n)
        proc.log.event("DEQUEUE", n, None, None, result)


def run_loop(proc, idle=60, stop=lambda: False):
    """Both lanes, each in its own thread, until stop()."""
    threads = [threading.Thread(target=run_lane, args=(proc, lane, idle, stop), name=f"mq-{lane}", daemon=True) for lane in LANES]
    for t in threads:
        t.start()
    for t in threads:
        t.join()


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
    for name in ("run", "list", "status", "resume"):
        sub.add_parser(name)
    sub.add_parser("once").add_argument("pr", type=int)
    for name in ("enqueue", "remove"):
        sub.add_parser(name).add_argument("prs", type=int, nargs="+")
    b = sub.add_parser("before"); b.add_argument("pr", type=int); b.add_argument("other", type=int)
    a = p.parse_args(argv)
    q = Queue(a.queue or default_queue())
    directory = os.path.dirname(os.path.abspath(q.path))
    log = Log(a.log or os.path.join(directory, "merge.log"))
    if a.cmd == "list":
        print("\n".join(map(str, q.numbers())))
    elif a.cmd == "enqueue":
        for n in a.prs:
            if not q.enqueue(n):
                print(f"#{n} is already queued (a pull request being processed stays queued until it is done)", file=sys.stderr)
    elif a.cmd == "remove":
        q.remove(*a.prs)
    elif a.cmd == "before":
        q.before(a.pr, a.other)
    elif a.cmd == "status":
        pause = Pause(directory, Gh(), log)
        print(f"queue: {q.numbers()}")
        print("paused: " + (open(pause.paused_file).read().strip() if pause.is_paused() else "no"))
        print(f"red main runs not acknowledged: {[(k, r) for k, r in pause.red_runs() if r not in pause.acked()] or 'none'}")
    elif a.cmd == "resume":
        red = Pause(directory, Gh(), log).resume()
        print(f"resumed; acknowledged {[r for _, r in red] or 'no red run'}")
    else:
        root = repo_root()
        proc = Processor(Gh(), q, log, root, ci_timeout=a.ci_timeout * 60)
        if a.cmd == "once":
            proc.process(a.pr)
        else:
            run_loop(proc)


if __name__ == "__main__":
    main()
