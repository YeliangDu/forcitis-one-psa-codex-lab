#!/usr/bin/env python3
"""Merge queue, step 1 of the self-built queue: pull requests are brought up to date, tested by the full CI and merged, in two lanes.

  mq.py run                       runner: a code lane and a docs lane, each takes the first pull request of its lane from the queue, processes it and removes it; waits when its lane is empty
  mq.py once <pr>                 process one pull request (no queue)
  mq.py enqueue <pr>...           append (a number already queued is not added twice; one being processed is reported)
  mq.py remove [--cancel] <pr>... take out; a pull request a worker is processing is refused (it cannot be taken back), with --cancel the worker is asked to stop at its next checkpoint, any time before the merge call (CANCELLED in the log)
  mq.py before <pr> <other>       put <pr> in front of <other> (appends it when it is not queued)
  mq.py list                      the queue, one number per line
  mq.py status                    queue, lanes, pause state
  mq.py resume <run id>...        acknowledge exactly the red main runs `status` shows (coordinator only) and let both lanes go on; a different set is refused
  mq.py train <merge_train.py args>   scripts/queue/merge_train.py: `train docs --branch B <session branch>@<sha>...` collects documentation commits (one by one, docs/ only, never an octopus merge)
  mq.py --self-test

Lanes. docs: every changed path is pure documentation (scripts/ci/relevant_change.py tier=docs: nothing a build or test reads). code: everything else, also the middle tier (docs a container-free test reads).
With branch protection's "branch must be up to date" off, GitHub no longer blocks a merge of a branch behind main; the queue does it itself: the code lane merges only a head that contains the current main and whose full
CI ran on that head, the docs lane syncs main and waits for the light run before merging. A head that lacks only commits whose files are all tier=docs (nothing a build or test reads, the same verdict as the release gate)
counts as up to date: no sync, no new CI run; a lack that includes a middle-tier or code file is synced and tested again. The safety net for the docs-only case is main's push alarm and the nightly full run (see Pause). main is changed only through this queue (SESSION_PLAYBOOK TEAM-47): a manual `gh pr merge` bypasses the lock and, with the protection off, GitHub no longer stops it. Merges are serialized by a lock, so two lanes never merge on a stale main: the one that comes second sees it is behind,
updates and goes through CI again.

Per pull request: cancel the CI runs of the other queued pull requests (not those of one labelled `急`, which is also picked first in its lane) (they are re-run after their own update; until then they only take runners), bring the branch up to date (gh update-branch; a pull request that
touches .github/workflows is updated by a local merge pushed over ssh, because the gh token lacks the workflow scope), label it `queued` (heavy CI steps run only with the label, scripts/ci/heavy_gate.py), wait for the
checks while cancelling the others' runs, rerun failed checks once, then merge with a merge commit, pinned to the head that was checked (`--match-head-commit`). Merge only when `gh pr checks` lists the three
required checks (backend verify, frontend verify, secret scan, none of them with the " (light)" suffix) and every one of them is `pass`; "no failure so far" is never enough (a head still running is not green).
Conflicts with main in generated files. When main moved so fast that a pull request conflicts only in products of docs/modernization/build_all.py, the queue does not send it back: it merges main in over ssh, runs build_all.py and
commits the regenerated products (AUTO-RESOLVE in the log), checks the result with `build_all.py --check` and pushes. Which files are products is not listed anywhere: build_all.py is run twice with different junk in every conflicted file and a file
is a product when both runs rewrite it to the same text; a conflict in a file a person wrote, a failing build_all.py or a failing check leaves the pull request as it was and it goes back to the author. `enqueue` only says that a pull request conflicts; the runner settles it when it takes it. The queue runs the pull request's own build_all.py and its steps on this machine, as its CI runs it on the runners.
A file that has moved out of the repository meanwhile (a generated product, now untracked and in .gitignore) which one side deleted and the other still changed is no conflict either: it is taken out of the index and kept on disk (the PR's merge commit says so).
Outcomes: MERGED, CONFLICT (text conflict with main in a file a person wrote: back to the author), HOLD (red after the rerun, CI timeout, closed, an update that could not be pushed, attempts exhausted: reported to the coordinator).

Pause. Before taking a pull request, and again right before a merge, the queue looks at main's push runs and nightly (schedule) runs of the ci workflow: the verdict run is the newest one that has ended and was not
cancelled (a push run is cancelled by the next push, so the newest run alone says nothing). A red verdict pauses both lanes (PAUSED in the log, file <queue dir>/mq.paused) until `mq.py resume <run id>...`; the resume
acknowledges exactly the runs named, so the same red run does not pause again, a new red run does. When gh cannot answer, the lanes wait without a pause file (WAIT-SIGNAL).
Docs merges and code pull requests. A docs pull request (tier=docs: nothing a build or test reads) that merges while a code pull request is in its CI does not make that one stale: the code pull request is counted as up to date
and merges on the CI it already has (no BEHIND-AT-MERGE, no new run), so the docs lane never waits for the code lane. That rests on relevant_change.py, whose self-test fails when a build or test reads a doc it does not list.
Main checkout (FF-MAIN). After every merge the queue fetches and fast-forwards the main checkout (the repository root, not a worktree) to origin/main, so the hooks and scripts there never run old versions.
It does so only when that checkout is on main, has no change to a tracked file and no commit origin/main lacks; otherwise it leaves it alone, logs ALERT and pauses both lanes until `mq.py resume` (without run ids) after a person has looked. One `mq.py run` or `once` per queue (flock on mq.runner.lock); the ssh update of two lanes never shares its scratch worktree (flock on <queue>.ssh-lock). A push to main runs the light steps and every container-free
unit test (ci.yml), the nightly the full ci: together they are the alarm for a merge whose tree no full run tested exactly as merged.

Gap scan. Every 30 minutes the runner looks at all open pull requests, whether or not the queue is empty (scripts/merge/gapscan.py): conflict, red CI, approved but not queued (a comment `调度批准入队 head <sha>`), no commit or comment for an hour.
It cannot message a session: a GAPS line in the log and <queue dir>/gaps.md say who owns what (branch prefix -> session, scripts/merge/session_map.json), the session watching the log forwards it. The same pull request and reason is reported once in 4 hours.

Queue file: one pull request number per line, optionally "<n> after=<m>[,<m>...]" (`mq.py enqueue <n> --after <m>`: n is not started until every m is MERGED, whatever lane either is in; WAIT-DEP in the log
while it waits and again every hour, HOLD when a dependency was closed unmerged (also when a merge train closed it: the content is in main, the message is only about the state; the dependent then goes back with `enqueue` without --after);
`once <n>` does not look at dependencies; after deploying this version restart the running runner first: an older runner does not understand "after=" lines and never starts them, and its enqueue would drop the dependency; a dependency cycle is refused at enqueue), anything else is kept as it is. Every change goes through flock on <queue>.lock and an atomic replace (temporary file, then rename), so an enqueue can no longer be
lost to the runner removing a finished entry (a raw `echo n >> merge.queue` or `sed -i` outside this script is not safe). Log: one line per event,
"<ISO time> <EVENT> pr=#<n> run=<ci run id or -> elapsed=<seconds since the pull request was taken>s [lane=code|docs] <detail>" (lane= once the lane of the pull request is known), appended to <queue dir>/merge.log and echoed. A failing runner step is logged as ERROR and retried
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
sys.path.insert(0, HERE)
import gapscan   # noqa: E402
REQUIRED = ("backend verify", "frontend verify", "secret scan")
# ci.yml's step that runs the pull request's new or changed test classes again (scripts/ci/repeat_new_tests.py): red there is a flaky new test, and a rerun that passes would let it in
URGENT_LABEL = "急"   # put on by the coordinator: the pull request is picked before the others of its lane and the queue never cancels its CI runs
REPEAT_STEP = "new or changed test classes, repeated"
OK = ("pass", "skipping")
LINE = re.compile(r"^\s*(\d+)(?:\s+after=([\d,]+))?\s*$")   # "<n>" or "<n> after=<m>[,<m>...]": n waits until every m is merged
LANES = ("code", "docs")
FAILING = ("failure", "timed_out", "startup_failure")


def sh(args, cwd=None, check=False, timeout=180):
    """A call that hangs (gh without network, a stuck ssh push) must not block a lane or the pause check: after `timeout` seconds it is a failed call (exit 124)."""
    try:
        r = subprocess.run(args, cwd=cwd, capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        r = subprocess.CompletedProcess(args, 124, "", f"timed out after {timeout} s")
    if check and r.returncode:
        raise SystemExit(f"{' '.join(args[:3])} failed ({r.returncode}): {(r.stderr or r.stdout).strip()[:300]}")
    return r


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
        with open(self.path + suffix, "w", encoding="utf-8") as f:
            f.write("".join(l + "\n" for l in lines if l))

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
        with self.locked():
            own = next((l.strip() for l in self._read() if (m := LINE.match(l)) and int(m.group(1)) == pr), str(pr))
            lines = [l for l in self._read() if not ((m := LINE.match(l)) and int(m.group(1)) == pr)]
            at = next((i for i, l in enumerate(lines) if (m := LINE.match(l)) and int(m.group(1)) == other), len(lines))
            lines.insert(at, own)
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

    def open_prs(self):
        """Every open pull request with what the gap scan (gapscan.py) needs, or None when gh cannot say."""
        r = sh(["gh", "pr", "list", "--state", "open", "--limit", str(gapscan.OPEN_LIMIT), "--json", "number,title,headRefName,headRefOid,isDraft,mergeable,statusCheckRollup,commits,comments,createdAt"], timeout=120)
        try:
            prs = json.loads(r.stdout)
        except ValueError:
            return None
        if r.returncode:
            return None
        for pr in prs:
            pr["comments"] = [dict(c, author=(c.get("author") or {}).get("login")) for c in pr.get("comments", [])]
        return prs

    def behind_by(self, n, head):
        r = sh(["gh", "api", f"repos/{{owner}}/{{repo}}/compare/main...{head}", "--jq", ".behind_by"])
        return int(r.stdout.strip()) if r.returncode == 0 and r.stdout.strip().isdigit() else None

    def behind_files(self, head):
        """Paths main changed since the branch point of `head` (what the head lacks), or None when gh cannot say or the list is cut off (the compare API lists at most 300 files)."""
        r = sh(["gh", "api", f"repos/{{owner}}/{{repo}}/compare/{head}...main", "--jq", "[.files[] | .filename, .previous_filename] | map(select(. != null)) | .[]"])
        if r.returncode:
            return None
        files = [x for x in r.stdout.splitlines() if x]
        n = sh(["gh", "api", f"repos/{{owner}}/{{repo}}/compare/{head}...main", "--jq", ".files | length"])
        return files if n.returncode == 0 and n.stdout.strip().isdigit() and int(n.stdout.strip()) < 300 else None

    def files(self, n):
        r = sh(["gh", "api", "--paginate", f"repos/{{owner}}/{{repo}}/pulls/{n}/files", "--jq", ".[] | .filename, .previous_filename"])
        return [x for x in r.stdout.splitlines() if x and x != "null"] if r.returncode == 0 else None

    def checks(self, n):
        r = sh(["gh", "pr", "checks", str(n), "--json", "name,bucket,link"])
        try:
            return json.loads(r.stdout)
        except ValueError:
            # a pull request whose CI has not registered anything yet: empty stdout, exit 0, "no checks reported on the '<branch>' branch" on stderr
            return [] if "no checks reported" in r.stderr else None

    def failed_steps(self, run):
        """Names of the steps that failed in a ci run, or None when gh cannot say."""
        r = sh(["gh", "run", "view", str(run), "--json", "jobs", "--jq", '.jobs[].steps[] | select(.conclusion == "failure") | .name'])
        return r.stdout.splitlines() if r.returncode == 0 else None

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
        """The verdict run of main for `event` (push | schedule): the newest run that has ended and was not cancelled or skipped ({'id', 'conclusion', 'sha'}), or None when there is none.
        A push run is cancelled by the next push (concurrency), so the newest run alone says nothing. Raises SignalError when gh cannot answer."""
        args = ["gh", "run", "list", "--workflow", "ci", "--event", event, "--limit", "20", "--json", "databaseId,status,conclusion,headSha,headBranch"]
        if event == "push":
            args += ["--branch", "main"]
        r = sh(args)
        try:
            rows = json.loads(r.stdout)
        except ValueError:
            raise SignalError(f"gh run list ({event}) gave no JSON: {(r.stderr or r.stdout).strip()[:120]}")
        for row in rows:
            if row.get("headBranch", "main") == "main" and row["status"] == "completed" and row["conclusion"] not in ("cancelled", "skipped", None, ""):
                return {"id": row["databaseId"], "status": "completed", "conclusion": row["conclusion"], "sha": row["headSha"]}
        return None

    def merge(self, n, head):
        r = sh(["gh", "pr", "merge", str(n), "--merge", "--match-head-commit", head])
        return r.returncode == 0, (r.stderr or r.stdout).strip()


BUILD_ALL = os.path.join("docs", "modernization", "build_all.py")


def moved_out_conflicts(wt, conflicted):
    """The conflicted paths that look like a file which has left the repository: deleted by one side and changed by the other (index stages 1+2 or 1+3), present at the merge base under that very path (not the made-up stage 1 of a rename),
    and ignored by a .gitignore of the merged tree (not by this machine's own exclude files). Only a candidate: it is settled as a generated product only when build_all.py is seen to rewrite it (regenerate_conflicts)."""
    stages = {}
    for entry in sh(["git", "-C", wt, "ls-files", "-u", "-z"]).stdout.split("\0"):
        info, _, name = entry.partition("\t")
        if name:
            stages.setdefault(name, set()).add(info.split()[2])
    base = sh(["git", "-C", wt, "merge-base", "HEAD", "MERGE_HEAD"]).stdout.strip()
    out = []
    for name in conflicted:
        if stages.get(name) not in ({"1", "2"}, {"1", "3"}):
            continue
        if not base or sh(["git", "-C", wt, "cat-file", "-e", f"{base}:{name}"]).returncode:
            continue
        verdict = sh(["git", "-C", wt, "check-ignore", "-v", "--no-index", "--", name]).stdout
        if verdict and os.path.basename(verdict.split(":", 1)[0]) == ".gitignore":
            out.append(name)
    return out


def finish_merge(wt, script, folder, label):
    """Commit the merge in progress once build_all.py has regenerated what it generates, and check the result; True only when `build_all.py --check` passes on it."""
    if any(line.startswith("??") for line in sh(["git", "-C", wt, "status", "--porcelain"]).stdout.splitlines()):
        return None   # build_all.py wrote a file nobody ignores: not committed blindly
    if sh(["git", "-C", wt, "add", "-A"]).returncode:
        return None
    if sh(["git", "-C", wt, "commit", "-q", "-m", "Merge origin/main (merge queue: " + label + ")"]).returncode:
        return None
    if sh([sys.executable, script, "--check"], cwd=folder, timeout=900).returncode:
        return None
    return True


def regenerate_conflicts(wt, conflicted):
    """Inside a merge in progress at `wt`: True when every conflicted path is a product of build_all.py (and the merge is now committed with the products regenerated), else None/False with the merge left as it is.
    The list of products is not written down anywhere: build_all.py is run twice with different junk in every conflicted file, and a file is a product when both runs rewrite it to the same text.
    A file a person writes keeps the junk (the two runs differ), so a conflict in it is never settled here. Anything unclear (no build_all.py, a run fails, the check afterwards fails) is no resolution."""
    script = os.path.join(wt, BUILD_ALL)
    if not conflicted or not os.path.exists(script):
        return None
    folder = os.path.dirname(script)
    if ".gitignore" in conflicted:   # what is ignored is not known until a person has settled it
        return False
    root = os.path.realpath(wt)
    for name in conflicted:   # junk is written to these paths: never through a symbolic link, never outside the work tree
        path = os.path.join(wt, name)
        if os.path.islink(path) or not os.path.realpath(os.path.dirname(path)).startswith(root + os.sep):
            return False
    moved_out = moved_out_conflicts(wt, conflicted)
    results = []
    for number in (1, 2):
        junk = f"MQ-PROBE-{number}\n"
        for name in conflicted:
            os.makedirs(os.path.dirname(os.path.join(wt, name)), exist_ok=True)
            with open(os.path.join(wt, name), "w", encoding="utf-8") as f:
                f.write(junk)
        run = sh([sys.executable, script], cwd=folder, timeout=600)
        if run.returncode:
            return None
        results.append({name: open(os.path.join(wt, name), "rb").read() if os.path.exists(os.path.join(wt, name)) else None for name in conflicted})
    if any(results[0][name] is None or results[0][name] != results[1][name] or b"MQ-PROBE" in results[0][name] for name in conflicted):
        return False
    for name in moved_out:   # a generated product that is no longer in the repository: out of the index, regenerated copy stays on disk (add -A would put it back)
        if sh(["git", "-C", wt, "rm", "-q", "--cached", "--", name]).returncode:
            return None
    return finish_merge(wt, script, folder, "regenerated by build_all.py: " + ", ".join(sorted(conflicted)) + ("; out of the index, ignored since: " + ", ".join(sorted(moved_out)) if moved_out else ""))


def update_over_ssh(repo, branch, wt):
    """Local merge of origin/main into the pull request branch, pushed over ssh (git, not the gh token): 'ok' | 'regenerated' | 'conflict' | 'error'.
    A merge that conflicts only in products of build_all.py is finished by regenerating them ('regenerated'); a conflict in anything a person wrote stays 'conflict'."""
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
            conflicted = [name for name in sh(["git", "-C", wt, "diff", "--name-only", "-z", "--diff-filter=U"]).stdout.split("\0") if name]
            if not conflicted:
                sh(["git", "-C", wt, "merge", "--abort"])
                return "error"
            if regenerate_conflicts(wt, conflicted):
                return "regenerated" if sh(["git", "-C", wt, "push", "-q", "origin", f"HEAD:{branch}"]).returncode == 0 else "error"
            sh(["git", "-C", wt, "merge", "--abort"])
            return "conflict"
        return "ok" if sh(["git", "-C", wt, "push", "-q", "origin", f"HEAD:{branch}"]).returncode == 0 else "error"
    finally:
        sh(["git", "-C", repo, "worktree", "remove", "-f", wt])


class Cancelled(Exception):
    """`mq.py remove --cancel` asked the worker to stop; raised at a checkpoint before the merge is made."""


class SignalError(Exception):
    """gh could not say how main's runs ended."""


def run_id(link):
    m = re.search(r"runs/(\d+)", link or "")
    return m.group(1) if m else None


def red_checks(checks):
    """Checks that are not pass or skipping, except a light check whose full-named twin is listed: the light run that the label's own run cancelled is no verdict."""
    full = {c["name"] for c in checks if not c["name"].endswith("(light)")}
    return [c for c in checks if c["bucket"] not in OK and not (c["name"].endswith("(light)") and c["name"][:-len("(light)")].rstrip() in full)]


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
    """<queue dir>/mq.paused holds the reason; mq.acked the red run ids a resume has acknowledged. File changes happen under a flock too: `mq.py resume` is another process."""

    def __init__(self, directory, gh, log):
        self.directory = directory
        self.paused_file, self.acked_file, self.gh, self.log = os.path.join(directory, "mq.paused"), os.path.join(directory, "mq.acked"), gh, log
        self.lock = threading.Lock()
        self.clock = time.monotonic
        self.last_signal_log = None   # monotonic time of the last WAIT-SIGNAL line; None until the first one

    @contextlib.contextmanager
    def exclusive(self):
        with self.lock, open(os.path.join(self.directory, "mq.pause.lock"), "a") as f:
            fcntl.flock(f, fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.flock(f, fcntl.LOCK_UN)

    def acked(self):
        try:
            return set(open(self.acked_file).read().split())
        except FileNotFoundError:
            return set()

    def red_runs(self):
        """[(kind, run id)]: the verdict run (newest ended, not cancelled) of main's pushes and of its nightly runs, when it ended red. Raises SignalError when gh cannot answer."""
        out = []
        for kind, event in (("push", "push"), ("nightly", "schedule")):
            run = self.gh.main_run(event)
            if run and run["conclusion"] in FAILING:
                out.append((kind, str(run["id"])))
        return out

    def is_paused(self):
        return os.path.exists(self.paused_file)

    def check(self):
        """True when the queue must not go on. Pauses it when an unacknowledged red run is there; when gh cannot say, the answer is also True (nothing is merged on a main nobody can see), without a pause file."""
        with self.exclusive():
            if self.is_paused():
                return True
            try:
                red = self.red_runs()
            except SignalError as e:
                if self.last_signal_log is None or self.clock() - self.last_signal_log > 300:
                    self.last_signal_log = self.clock()
                    self.log.event("WAIT-SIGNAL", None, None, None, f"cannot read main's runs ({e}); both lanes wait")
                return True
            fresh = [(kind, rid) for kind, rid in red if rid not in self.acked()]
            if not fresh:
                return False
            reason = ", ".join(f"main {kind} run {rid} is red" for kind, rid in fresh)
            with open(self.paused_file, "w", encoding="utf-8") as f:
                f.write(reason + "\n")
            self.log.event("PAUSED", None, fresh[0][1], None, reason + "; both lanes wait for `mq.py resume <run id>...`")
            return True

    def hold(self, reason):
        """Pauses both lanes for a reason no red run explains (the main checkout could not be fast-forwarded); `mq.py resume` with no run id lets them go on once a person has looked."""
        with self.exclusive(), open(self.paused_file, "w", encoding="utf-8") as f:
            f.write(reason + "\n")

    def resume(self, ack=None):
        """Acknowledge exactly the red runs named (None: whatever is red now, for the self-test); a different set is refused, so a run that turned red after the coordinator looked is never acknowledged blind."""
        with self.exclusive():
            red = self.red_runs()
            if ack is not None and sorted(ack) != sorted(rid for _, rid in red):
                raise SystemExit(f"red runs now: {[rid for _, rid in red] or 'none'}; resume names {sorted(ack) or 'none'}: give exactly the red runs you looked at")
            with open(self.acked_file, "a", encoding="utf-8") as f:
                f.write("".join(rid + "\n" for _, rid in red))
            with contextlib.suppress(FileNotFoundError):
                os.unlink(self.paused_file)
            self.log.event("RESUMED", None, None, None, f"acknowledged red runs: {', '.join(rid for _, rid in red) or 'none'}")
            return red


@contextlib.contextmanager
def runner_lock(directory):
    """One `mq.py run` or `once` per queue: two of them would cancel each other's CI runs and each think the other's pull request is free."""
    with open(os.path.join(directory, "mq.runner.lock"), "a") as f:
        try:
            fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SystemExit("another `mq.py run` or `once` is working on this queue")
        try:
            yield
        finally:
            fcntl.flock(f, fcntl.LOCK_UN)


def docs_only(repo):
    """files -> True when scripts/ci/relevant_change.py says tier=docs for every path (nothing a build or test reads); an empty list, a failing or silent script is False."""
    def check(files):
        if not files:
            return False
        with tempfile.NamedTemporaryFile("w", suffix=".paths", delete=False, encoding="utf-8") as f:
            f.write("\n".join(files) + "\n")
        try:
            r = sh([sys.executable, os.path.join(repo, "scripts/ci/relevant_change.py"), "--paths-from", f.name], cwd=repo)
        finally:
            os.unlink(f.name)
        return r.returncode == 0 and "tier=docs" in r.stdout.splitlines()
    return check


def default_lane(repo):
    """'docs' when every path of the pull request is docs (docs_only), else 'code'; a lookup that fails is 'code' (the stricter lane)."""
    check = docs_only(repo)
    return lambda gh, n: "docs" if check(gh.files(n)) else "code"


class Processor:
    def __init__(self, gh, queue, log, repo, sleep=time.sleep, updater=update_over_ssh, ci_timeout=90 * 60, wt=None, lane=None, pause=None, docs_only_fn=None, main_checkout=None):
        self.gh, self.queue, self.log, self.repo, self.sleep = gh, queue, log, repo, sleep
        self.updater, self.ci_timeout = updater, ci_timeout
        self.wt = wt or os.path.join(os.path.dirname(os.path.abspath(queue.path)), "mq-wt")
        self.lane_of = lane or default_lane(repo)
        self.docs_only = docs_only_fn or docs_only(repo)
        self.pause = pause or Pause(os.path.dirname(os.path.abspath(queue.path)), gh, log)
        self.lanes, self.busy = {}, set()
        self.waiting, self.wait_repeat = {}, 3600   # pull request -> (the dependencies it was last logged as waiting for, when): logged again when they change and every wait_repeat seconds, so a dependency that never merges stays loud
        self.guard = threading.Lock()
        self.cancel_seen = set()
        self.own_head = {}   # n -> the head after the queue's own last push (update, settle): a different head later is the author's push
        self.main_checkout = main_checkout

    def lane(self, n, head=None):
        key = (n, head)
        if key not in self.lanes:
            self.lanes[key] = self.lane_of(self.gh, n)
        return self.lanes[key]

    def pick(self, lane):
        """First queued pull request of this lane that no worker has; one labelled `急` (URGENT_LABEL, put on by the coordinator) comes before the others."""
        views = {}
        for n in self.queue.numbers():
            with self.guard:
                if n in self.busy:
                    continue
            views[n] = self.gh.view(n)
        for n in sorted(views, key=lambda m: URGENT_LABEL not in (views[m] or {}).get("labels", ())):
            with self.guard:
                if n in self.busy:
                    continue
            v = views[n]
            if self.lane(n, v["head"] if v else None) == lane:
                self.log.lanes[n] = lane
                if not self.dependencies_met(n):
                    continue
                with self.guard:
                    if n in self.busy:
                        continue
                    self.busy.add(n)
                try:
                    claimed = self.queue.claim(n)
                except BaseException:   # the claim itself failed (disk): nobody holds it, it must not stay busy
                    self.release(n)
                    raise
                if not claimed:   # taken out of the queue since the look: not started
                    self.log.event("SKIPPED", n, None, None, "taken out of the queue before it was started")
                    self.release(n)
                    continue
                return n
        return None

    def dependencies_met(self, n):
        """True when every pull request n was queued after is merged; otherwise logs once per change of what it waits for (WAIT-DEP) and False. A dependency closed unmerged puts n on HOLD and takes it out."""
        unmet, closed, unknown = [], [], []
        for m in sorted(self.queue.deps(n)):
            v = self.gh.view(m)
            if v and v["state"] == "MERGED":
                continue
            if v is None:
                unknown.append(m)
            (closed if v and v["state"] == "CLOSED" else unmet).append(m)
        if closed:
            self.log.event("HOLD", n, None, None, f"dependency #{closed[0]} was closed without merge")
            self.queue.remove(n)
            self.log.event("DEQUEUE", n, None, None, "HOLD")
            self.waiting.pop(n, None)
            return False
        if unmet:
            now = time.monotonic()
            if self.waiting.get(n, (None,))[0] != tuple(unmet) or now - self.waiting[n][1] >= self.wait_repeat:
                queued = set(self.queue.numbers())
                self.log.event("WAIT-DEP", n, None, None, f"waits for {' '.join('#' + str(m) + ('' if m in queued else ' (not in the queue)') for m in unmet)} to be merged" + (f"; gh could not tell the state of {' '.join('#' + str(m) for m in unknown)}" if unknown else ""))
                self.waiting[n] = (tuple(unmet), now)
            return False
        self.waiting.pop(n, None)
        return True

    def stale(self, n, head):
        """None when gh cannot say; 0 when the head contains main, or lacks only commits that changed nothing a build or test reads (docs only: counted as up to date, no sync, no new CI run);
        else the number of main commits the head lacks."""
        behind = self.gh.behind_by(n, head)
        if not behind:
            return behind
        files = self.gh.behind_files(head)
        # a Python file under docs/ is check logic (build_all.py, check_*.py run in every pull request's secret scan job): it is no plain documentation for this purpose, the pull request is tested again against it
        if files is not None and self.docs_only(files) and not any(f.endswith(".py") for f in files):
            return 0
        return behind

    def cancel_others(self, head, since):
        for other in self.queue.numbers():
            with self.guard:
                if other == head or other in self.busy:   # a pull request the other lane is working on keeps its runs
                    continue
            v = self.gh.view(other)
            if not v or URGENT_LABEL in v["labels"]:   # an urgent pull request keeps its runs: it runs first, whoever is in front
                continue
            for run in self.gh.active_runs(v["branch"]):
                with self.guard:
                    if other in self.busy:   # picked up by the other lane meanwhile
                        break
                if self.gh.cancel_run(run):
                    self.log.event("CANCEL-RUN", other, run, since, f"waiting behind #{head}")

    def settle_dirty(self, n, v, since):
        """A pull request GitHub reports as conflicting: the update merges main into it and, when the conflict is only in products of build_all.py, regenerates them (AUTO-RESOLVE).
        (new view, None) when it is settled; (None, 'conflict') when a person has to settle it (a file a person wrote conflicts, or GitHub still sees a conflict a few minutes after the push); (None, 'error') when the update itself failed."""
        res = self.update(n, v["branch"], since, via_gh=False)
        if res == "conflict":
            return None, "conflict"
        if res != "ok":
            return None, "error"
        for _ in range(5):   # GitHub takes a while to see the push: DIRTY or UNKNOWN right afterwards is not yet an answer
            self.sleep(30)
            now = self.gh.view(n) or v
            if now["merge"] not in ("DIRTY", "UNKNOWN"):
                return now, None
        return None, "conflict"

    def touches_workflows(self, n):
        """A pull request that changes .github/workflows cannot be updated with `gh pr update-branch`: the gh token lacks the workflow scope, GitHub accepts the call and the push is dropped afterwards (exit 0, head unchanged).
        A file list gh cannot give counts as touching: the ssh update is the safe way."""
        files = self.gh.files(n)
        return files is None or any(f.startswith(".github/workflows/") for f in files)

    def update(self, n, branch, since, via_gh=True):
        if via_gh and not self.touches_workflows(n) and self.gh.update_branch(n):
            self.log.event("UPDATE", n, None, since, "gh update-branch")
            return "ok"
        with self.queue.locked(".ssh-lock"):   # one scratch worktree, one fetch and push at a time, whatever lane asks
            res = self.updater(self.repo, branch, self.wt)
        if res == "regenerated":
            self.log.event("AUTO-RESOLVE", n, None, since, "main conflicted only in products of build_all.py: merged, regenerated, pushed over ssh")
            return "ok"
        self.log.event("SSH-UPDATE" if res == "ok" else f"SSH-UPDATE-{res.upper()}", n, None, since, "local merge pushed over ssh")
        return res

    def wait_checks(self, n, since):
        """Checks once none is pending; None when they stayed pending past the CI timeout. A failed gh call is no answer: asked again."""
        for _ in range(20):
            self.checkpoint(n)   # until the full-named backend check is listed: right after the label only the light run's checks may be there
            listed = self.gh.checks(n) or []
            if any(c["name"].startswith("backend verify") and not c["name"].endswith("(light)") for c in listed):
                break
            self.sleep(15)
        waited = 0
        while True:
            self.checkpoint(n)
            checks = self.gh.checks(n)
            if checks and not any(c["bucket"] == "pending" for c in checks):
                return checks
            if waited >= self.ci_timeout:
                return [] if checks == [] else None   # nothing ever registered, or still running: the two HOLD messages differ
            self.cancel_others(n, since)
            self.sleep(30)
            waited += 30

    def release(self, n):
        """No worker holds n any more: the mark goes, and busy goes whatever happens to the mark."""
        try:
            self.queue.unmark_working(n)
        finally:
            with self.guard:
                self.busy.discard(n)

    def checkpoint(self, n):
        """Where a cancel request is honoured: anything before the merge call. Once gh has been asked to merge, it is too late and the merge stands."""
        if self.queue.cancel_requested(n):
            raise Cancelled(n)

    def process(self, n):
        since = time.monotonic()
        if n not in self.log.lanes:   # `once` takes a pull request without pick
            self.log.lanes[n] = self.lane(n)
        if self.queue.working().get(n) != os.getpid():   # `once` takes a pull request without pick
            self.queue.mark_working(n)
        self.log.event("START", n, None, since)
        try:
            return self._process(n, since)
        except Cancelled:
            with contextlib.suppress(Exception):
                self.gh.label(n, "queued", False)   # not queued any more: the label would start heavy CI for nothing
            self.log.event("CANCELLED", n, None, since, "stopped before the merge on request (mq.py remove --cancel); updates already pushed to the branch stay, the queued label is taken off")
            return "CANCELLED"
        finally:
            if self.queue.cancel_requested(n):
                self.cancel_seen.add(n)   # run_lane must not take out an entry queued again after `remove --cancel`, whatever the round ended with
            self.release(n)

    def head_moved(self, n, v):
        """The head the pull request should have, when it has another one: what was approved at enqueue (first look) or what the queue itself left after its last push (later looks). None when nothing is wrong or nothing is recorded."""
        expected = self.own_head.get(n) or self.queue.head_of(n)
        return expected if expected and expected != v["head"] else None

    def head_changed(self, n, v, expected, since):
        """The author pushed after the head was approved: not merged, taken out; the log line (HEAD-CHANGED, with both shas and the owning session) is what the watching session forwards to the author and the coordinator."""
        try:
            owner = gapscan.author_session(v["branch"], gapscan.load_prefixes())
        except (OSError, ValueError, KeyError):
            owner = f"?({v['branch']})"
        self.queue.drop_head(n)
        self.own_head.pop(n, None)
        with contextlib.suppress(Exception):
            self.gh.label(n, "queued", False)
        self.log.event("HEAD-CHANGED", n, None, since, f"approved {expected[:9]} now {v['head'][:9]} owner {owner}: not merged, out of the queue; report the new head and have it approved again")
        return "HEAD-CHANGED"

    def hold(self, n, run, since, why):
        self.log.event("HOLD", n, run, since, why)
        return "HOLD"

    def _process(self, n, since):
        self.cancel_others(n, since)
        attempt, moved, refused = 1, 0, 0   # attempts count red checks and a head that stopped being green; moved: main advanced during the full CI; refused: the merge itself was refused (own counter, growing pause)
        while attempt <= 4:
            self.checkpoint(n)
            v = self.gh.view(n)
            if v is None:
                return self.hold(n, None, since, "gh could not tell the state of the pull request")
            if v["state"] == "MERGED":
                self.log.event("MERGED", n, None, since, "already merged")
                return "MERGED"
            if v["state"] != "OPEN":
                return self.hold(n, None, since, f"the pull request is {v['state']}")
            moved_head = self.head_moved(n, v)
            if moved_head:
                return self.head_changed(n, v, moved_head, since)
            if v["merge"] == "DIRTY":
                v, why = self.settle_dirty(n, v, since)
                if why == "error":
                    return self.hold(n, None, since, "the update could not be pushed")
                if v is None:
                    self.log.event("CONFLICT", n, None, since, "back to the author (text conflict)")
                    return "CONFLICT"
            behind = self.stale(n, v["head"])
            if v["merge"] == "BEHIND" or (behind or 0) > 0:
                self.checkpoint(n)
                res = self.update(n, v["branch"], since)
                if res != "ok":
                    if res == "conflict":
                        self.log.event("CONFLICT", n, None, since, "back to the author (text conflict with main)")
                        return "CONFLICT"
                    return self.hold(n, None, since, "the update could not be pushed")
                self.sleep(30)
                v = self.gh.view(n) or v
            self.checkpoint(n)
            self.own_head[n] = v["head"]
            self.queue.set_head(n, v["head"])   # kept on disk too: a runner restarted in the middle must not take the queue's own update for the author's push
            if "queued" not in v["labels"]:
                if self.gh.label(n, "queued", True):
                    self.log.event("LABEL-QUEUED", n, None, since)
                self.sleep(20)
            checks = self.wait_checks(n, since)
            if checks is None:
                return self.hold(n, None, since, f"CI still pending after {self.ci_timeout // 60} minutes")
            if not checks:
                return self.hold(n, None, since, "gh pr checks lists no check at all")
            red = red_checks(checks)
            if red:
                runs = sorted({r for c in red if (r := run_id(c.get("link")))}, key=int)
                now = self.gh.view(n) or v
                if (self.stale(n, now["head"]) or 0) > 0:
                    self.log.event("BEHIND-AFTER-FAIL", n, runs[0] if runs else None, since, "update and run again")
                    attempt += 1
                    continue
                steps = {r: self.gh.failed_steps(r) for r in runs}
                unknown = [r for r, st in steps.items() if st is None]
                if unknown:   # gh could not say which step failed: the one rerun still happens (the lenient direction), but it is on record
                    self.log.event("FAILED-STEPS-UNKNOWN", n, unknown[0], since, "could not read the failed steps; a failed repeat step would not be recognised")
                repeated = [r for r, st in steps.items() if REPEAT_STEP in (st or [])]
                if repeated:   # a new test that fails when it runs again is flaky: no rerun (it would pass the second time and get in), straight to the author
                    return self.hold(n, ",".join(repeated), since, f"a new or changed test class failed its repeat runs ({REPEAT_STEP}): not rerun, back to the author")
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
            if result == "head-changed":
                now = self.gh.view(n) or v
                return self.head_changed(n, now, self.head_moved(n, now) or self.own_head.get(n) or "?", since)
            if result == "moved":
                moved += 1
                if moved > 6:
                    return self.hold(n, None, since, f"main moved {moved} times while the full CI of this pull request ran")
                continue
            if result == "changed":
                attempt += 1
                continue
            if result == "refused":
                if (self.gh.view(n) or {}).get("merge") == "BEHIND":   # main moved between the look and the merge (strict protection): a sync, not a refusal that counts
                    moved += 1
                    if moved > 6:
                        return self.hold(n, None, since, f"main moved {moved} times while this pull request waited for its merge")
                    continue
                refused += 1
                if refused >= 5:
                    return self.hold(n, None, since, f"merge refused {refused} times")
                self.sleep(20 * 2 ** (refused - 1))
                continue
            return result
        return self.hold(n, None, since, "attempts exhausted")

    def ff_main(self, n, since):
        """After a merge: the main checkout (where the hooks and scripts every session reads live) is fast-forwarded to origin/main, so it never runs old code for hours.
        It is touched only when it is on main, has no change to a tracked file and no commit origin/main lacks; otherwise it is left alone, the log says ALERT and both lanes pause until a person has looked
        (`mq.py resume`). A failed fetch is only logged: the next merge tries again."""
        checkout = self.main_checkout
        if not checkout:
            return
        with self.queue.locked(".ff-lock"):   # two lanes merging one after the other must not fetch and merge in the main checkout at the same time (index.lock)
            self._ff_main(n, since, checkout)

    def _ff_main(self, n, since, checkout):
        for attempt in range(3):   # a fetch or fast-forward that fails because another git command holds a lock in that checkout is tried again before anybody is called
            fetched = sh(["git", "-C", checkout, "fetch", "--quiet", "origin", "main"], timeout=120)
            if fetched.returncode == 0:
                break
            self.sleep(5)
        else:
            self.log.event("ALERT", n, None, since, f"FF-MAIN: fetch failed, main checkout not updated: {fetched.stderr.strip()[:200]}")
            return
        branch = sh(["git", "-C", checkout, "symbolic-ref", "--short", "-q", "HEAD"]).stdout.strip()
        dirty = sh(["git", "-C", checkout, "status", "--porcelain", "--untracked-files=no"]).stdout.strip()
        ahead = sh(["git", "-C", checkout, "rev-list", "--count", "origin/main..HEAD"]).stdout.strip()
        why = None
        if branch != "main":
            why = f"the main checkout is on {branch or 'a detached HEAD'}, not main"
        elif dirty:
            why = "the main checkout has changes to tracked files"
        elif ahead != "0":
            why = f"the main checkout has {ahead or 'an unknown number of'} commit(s) origin/main lacks"
        else:
            for attempt in range(3):
                merged = sh(["git", "-C", checkout, "merge", "--ff-only", "--quiet", "origin/main"])
                if merged.returncode == 0 or "lock" not in merged.stderr:
                    break
                self.sleep(5)
            if merged.returncode == 0:
                self.log.event("FF-MAIN", n, None, since, sh(["git", "-C", checkout, "rev-parse", "--short", "HEAD"]).stdout.strip())
                return
            why = f"fast-forward refused: {merged.stderr.strip()[:200]}"
        self.log.event("ALERT", n, None, since, f"FF-MAIN: {why}; main checkout left as it is, both lanes pause for `mq.py resume`")
        self.pause.hold(f"FF-MAIN: {why}")

    def merge_when_fresh(self, n, since, checks):
        """Under the merge lock: no red main run pauses the queue, the head is the one that was checked, it contains the current main, the checks are all green on it; then the merge, pinned to that head.
        Returns 'MERGED', 'head-changed' (the author pushed: MRG-06), 'moved' (behind main: update and test again), 'changed' (checks not green any more), 'refused' (gh refused the merge: logged with its message), or 'HOLD'."""
        with self.queue.merge_lock():
            while self.pause.check():
                self.checkpoint(n)
                self.log.event("WAIT-PAUSED", n, None, since, "main is red: waiting for `mq.py resume`")
                self.sleep(60)
            v = self.gh.view(n)
            if v is None or v["state"] != "OPEN":
                return self.hold(n, None, since, "the pull request changed state before the merge")
            head = v["head"]
            if self.head_moved(n, v):   # the author pushed while the CI ran: what ran is not what would be merged
                return "head-changed"
            behind = self.stale(n, head)
            if behind is None:
                return self.hold(n, None, since, "could not tell whether the head contains main")
            if v["merge"] == "BEHIND" and not behind:
                # the protection "branch must be up to date" is still on: GitHub counts a head that lacks only docs commits as behind and would refuse the merge; no attempt, sync and test again
                self.log.event("BEHIND-AT-MERGE", n, None, since, "GitHub reports BEHIND (protection still requires an up-to-date branch) although only docs commits are missing: update and run again")
                return "moved"
            if behind > 0:
                self.log.event("BEHIND-AT-MERGE", n, None, since, f"main moved by {behind} commit(s) while the full CI ran: update and run again")
                return "moved"
            fresh = self.gh.checks(n)
            if not fresh or not required_state(fresh)[0] or red_checks(fresh):
                self.log.event("CHECKS-CHANGED", n, None, since, "the checks are no longer all green on this head: run again")
                return "changed"
            self.checkpoint(n)   # the last one: under the merge lock, right before gh is asked
            ok, message = self.gh.merge(n, head)
            if ok:
                self.log.event("MERGED", n, run_id(checks[0].get("link")), since, head[:10])
        if ok:
            self.ff_main(n, since)
            return "MERGED"
        v = self.gh.view(n) or {}
        names = [f"{c['name']}={c['bucket']}" for c in red_checks(self.gh.checks(n) or [])]
        self.log.event("RETRY-MERGE", n, None, since, f"refused: mergeState={v.get('merge')} failing={names or 'none'} gh: {message}")
        return "refused"


def run_lane(proc, lane, idle=60, stop=lambda: False):
    """One lane of the runner: pause check, pick, process, take out. A failing step is logged and retried with a growing pause; the third failure in a row puts the pull request on HOLD.
    Nothing a step raises ends the lane: the whole round is inside the try."""
    failures = {}
    while not stop():
        n = None
        try:
            if proc.pause.check():
                proc.sleep(idle)
                continue
            n = proc.pick(lane)
            if n is None:
                proc.sleep(idle)
                continue
            try:
                result = proc.process(n)
            finally:
                proc.release(n)   # whatever happened, no worker holds it any more
            failures.pop(n, None)
            cancelled = result == "CANCELLED" or n in proc.cancel_seen   # `remove --cancel` took it out already; it may have been queued again since
            proc.cancel_seen.discard(n)
            if not cancelled:
                proc.queue.remove(n)
                proc.queue.drop_head(n)
                proc.own_head.pop(n, None)
            proc.log.event("DEQUEUE", n, None, None, result)
        except (Exception, SystemExit) as e:
            if n is None:
                proc.log.event("ERROR", None, None, None, f"{lane} lane: {type(e).__name__}: {e}")
                proc.sleep(60)
                continue
            failures[n] = failures.get(n, 0) + 1
            proc.log.event("ERROR", n, None, None, f"{type(e).__name__}: {e} (failure {failures[n]} in a row)")
            if failures[n] < 3:
                proc.sleep(30 * 2 ** failures[n])
                continue
            failures.pop(n, None)
            try:
                proc.log.event("HOLD", n, None, None, "the runner failed three times on this pull request")
                proc.queue.remove(n)
                proc.log.event("DEQUEUE", n, None, None, "HOLD")
            except (Exception, SystemExit) as again:   # the lane lives on even when taking the pull request out fails
                print(f"mq: could not take #{n} out of the queue: {again}", file=sys.stderr, flush=True)
                proc.sleep(60)


def run_scan(proc, directory, every=gapscan.SCAN_EVERY, first=60, stop=lambda: False):
    """The gap scan thread: first after `first` seconds, then every `every`, whether or not the queue is empty. Nothing a scan raises ends the thread."""
    wait = first
    while not stop():
        proc.sleep(wait)
        if stop():
            break
        wait = every
        try:
            gapscan.scan(proc.gh, proc.queue, directory, proc.log)
        except (Exception, SystemExit) as e:
            proc.log.event("ERROR", None, None, None, f"gap scan: {type(e).__name__}: {e}")


def run_loop(proc, idle=60, stop=lambda: False, directory=None):
    """Both lanes, each in its own thread, until stop(); with a queue directory also the gap scan."""
    threads = [threading.Thread(target=run_lane, args=(proc, lane, idle, stop), name=f"mq-{lane}", daemon=True) for lane in LANES]
    if directory:
        threads.append(threading.Thread(target=run_scan, args=(proc, directory), kwargs=dict(stop=stop), name="mq-gap-scan", daemon=True))
    for t in threads:
        t.start()
    for t in threads:
        t.join()


def train_module():
    spec = importlib.util.spec_from_file_location("merge_train", os.path.join(HERE, "..", "queue", "merge_train.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main_checkout():
    """The main checkout: the first entry of `git worktree list` (a bare repository has none: None)."""
    r = sh(["git", "worktree", "list", "--porcelain"])
    first = r.stdout.split("\n\n")[0].splitlines() if r.returncode == 0 else []
    if not first or not first[0].startswith("worktree ") or "bare" in first:
        return None
    return first[0][len("worktree "):]


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


def positive(text):
    if not text.isdigit() or int(text) < 1:
        raise argparse.ArgumentTypeError(f"{text!r} is not a pull request number")
    return int(text)


def note_conflicts_at_enqueue(numbers):
    """Only a note before the pull request waits: the settling itself (build_all.py runs for minutes under the ssh lock) is left to the runner, which does it when it takes the pull request."""
    try:
        gh = Gh()
        for n in numbers:
            v = gh.view(n)
            if v and v["state"] == "OPEN" and v["merge"] == "DIRTY":
                print(f"#{n} conflicts with main: the queue tries to settle it when it takes it (only conflicts in products of build_all.py are settled; in a file a person wrote the author has to merge main)", file=sys.stderr)
    except (Exception, SystemExit) as error:
        print(f"mq: could not look at conflicts before queueing: {error}", file=sys.stderr)


def record_heads(q, numbers):
    """The head each pull request has at enqueue is the head that was approved (also for one queued again: that is a new approval). gh not answering leaves the pull request without a check, said out loud."""
    gh = Gh()
    for n in numbers:
        v = gh.view(n)
        if v and v["state"] == "OPEN":
            q.set_head(n, v["head"])
        else:
            q.drop_head(n)   # an older approval of the same number is not this one
            print(f"mq: could not read the head of #{n}: it is queued without the head check", file=sys.stderr)


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
    for name in ("run", "list", "status"):
        sub.add_parser(name)
    sub.add_parser("resume").add_argument("runs", nargs="*", help="the red run ids that paused the queue, exactly as `status` shows them")
    sub.add_parser("once").add_argument("pr", type=int)
    e = sub.add_parser("enqueue")
    e.add_argument("prs", type=int, nargs="+")
    e.add_argument("--after", type=positive, action="append", default=[], metavar="M", help="start only once pull request M is merged (repeatable; whatever lane either is in)")
    r = sub.add_parser("remove")
    r.add_argument("prs", type=int, nargs="+")
    r.add_argument("--cancel", action="store_true", help="also stop a worker that is processing one of them (before its merge); without it such a pull request is refused")
    b = sub.add_parser("before"); b.add_argument("pr", type=int); b.add_argument("other", type=int)
    a = p.parse_args(argv)
    q = Queue(a.queue or default_queue())
    directory = os.path.dirname(os.path.abspath(q.path))
    log = Log(a.log or os.path.join(directory, "merge.log"))
    if a.cmd == "list":
        for l in q._read():
            if l.strip() and not LINE.match(l):
                print(f"mq: line not understood, never started, kept as it is: {l!r}", file=sys.stderr)
        print("\n".join(f"{n}" + (f" after={','.join(map(str, sorted(q.deps(n))))}" if q.deps(n) else "") for n in q.numbers()))
    elif a.cmd == "enqueue":
        try:
            added = q.enqueue(*a.prs, after=a.after)
        except ValueError as e:
            raise SystemExit(f"nothing queued: {e}")
        note_conflicts_at_enqueue(a.prs)
        record_heads(q, a.prs)
        for n in a.prs:
            if n not in added:
                print(f"#{n} is already queued" + (f"; now waits for {sorted(q.deps(n))} (a pull request already being processed does not look at it again)" if a.after else " (a pull request being processed stays queued until it is done)"), file=sys.stderr)
    elif a.cmd == "remove":
        busy = q.remove_checked(set(a.prs), a.cancel)
        for n in set(a.prs) - set(busy):
            q.drop_head(n)
        if busy and not a.cancel:
            raise SystemExit("nothing removed: " + ", ".join(f"#{n}" for n in sorted(busy)) + " is being processed by the runner (pid " + ", ".join(str(busy[n]) for n in sorted(busy)) + "): it cannot be taken back, "
                             "what it has pushed or merged stays. `mq.py remove --cancel <pr>` asks the worker to stop at its next checkpoint (anything before the merge is made); once the merge call has been made it is too late.")
        for n in sorted(busy):
            print(f"#{n} is being processed: cancel requested; the worker stops before the merge unless it is already merging (the log then says CANCELLED, MERGED, HOLD or CONFLICT)", file=sys.stderr)
    elif a.cmd == "before":
        q.before(a.pr, a.other)
    elif a.cmd == "status":
        pause = Pause(directory, Gh(), log)
        print(f"queue: {q.numbers()}")
        print(f"being processed: {sorted(q.working()) or 'none'}")
        print("paused: " + (open(pause.paused_file).read().strip() if pause.is_paused() else "no"))
        try:
            red = pause.red_runs()
            print(f"red main runs: {[(k, r, 'acknowledged' if r in pause.acked() else 'NEW') for k, r in red] or 'none'}")
            if pause.is_paused():
                print(f"to resume: mq.py resume {' '.join(r for _, r in red)}".rstrip())
        except SignalError as e:
            print(f"red main runs: unknown ({e})")
    elif a.cmd == "resume":
        try:
            red = Pause(directory, Gh(), log).resume(a.runs)
        except SignalError as e:
            raise SystemExit(f"cannot read main's runs ({e}); try again")
        print(f"resumed; acknowledged {[r for _, r in red] or 'no red run'}")
    else:
        root = repo_root()
        checkout = main_checkout()
        if not checkout:
            print("mq: no main checkout found (bare repository?): FF-MAIN is switched off", file=sys.stderr)
        proc = Processor(Gh(), q, log, root, ci_timeout=a.ci_timeout * 60, main_checkout=checkout)
        with runner_lock(directory):
            q.reset_working()
            if a.cmd == "once":
                proc.process(a.pr)
            else:
                run_loop(proc, directory=directory)


if __name__ == "__main__":
    main()
