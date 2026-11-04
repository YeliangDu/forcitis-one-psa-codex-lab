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
Conflicts with main: the queue merges main in (over ssh when gh cannot); a text conflict goes back to the author (CONFLICT).
Release. Before it spends CI on a pull request, and again under the merge lock right before the merge, the queue reads the reviews and comments: the newest statement that speaks for the head must be a 通过 (release_verdict). When nothing speaks for the head and it only merges main into an earlier head (the queue's own update: second parent on main, the tree of a clean merge), the earlier head's verdict carries over.
A first-round review speaks for the head when it was posted on it or on a commit whose patch-id (git patch-id --verbatim, computed by the queue) equals the head's (a rebase, or the queue's own merge of main, that leaves the change as it was);
a dispatcher comment `调度批准入队 head <sha>` (the coordinator letting a 必改 fix through) speaks for the head it names or one with the same patch-id, but only on a branch that names a session of policy.json sessions.config other than lead: on lead's own branch, an old or unknown branch name, or no branch it does not count and a first-round review must pass (#856). Docs-lane pull requests need no review unless they touch a guarded path (policy.json review.final_paths). Not released: HOLD.
Read-back. After the merge call GitHub must show the pull request MERGED with the pinned head as the second parent of the merge commit; otherwise ALERT READBACK and both lanes pause until `mq.py resume`.
Outcomes: MERGED, CONFLICT (text conflict with main in a file a person wrote: back to the author), HOLD (red after the rerun, CI timeout, closed, not released, an update that could not be pushed, attempts exhausted: reported to the coordinator).

Pause. Before taking a pull request, and again right before a merge, the queue looks at main's push runs and nightly (schedule) runs of the ci workflow: the verdict run is the newest one that has ended and was not
cancelled (a push run is cancelled by the next push, so the newest run alone says nothing). A red verdict pauses both lanes (PAUSED in the log, file <queue dir>/mq.paused) until `mq.py resume <run id>...`; the resume
acknowledges exactly the runs named, so the same red run does not pause again, a new red run does. When gh cannot answer, the lanes wait without a pause file (WAIT-SIGNAL).
Docs merges and code pull requests. A docs pull request (tier=docs: nothing a build or test reads) that merges while a code pull request is in its CI does not make that one stale: the code pull request is counted as up to date
and merges on the CI it already has (no BEHIND-AT-MERGE, no new run), so the docs lane never waits for the code lane. That rests on relevant_change.py, whose self-test fails when a build or test reads a doc it does not list.
Main checkout (FF-MAIN). After every merge the queue fetches and fast-forwards the main checkout (the repository root, not a worktree) to origin/main, so the hooks and scripts there never run old versions.
It does so only when that checkout is on main, has no change to a tracked file and no commit origin/main lacks; otherwise it leaves it alone, logs ALERT and pauses both lanes until `mq.py resume` (without run ids) after a person has looked. One `mq.py run` or `once` per queue (flock on mq.runner.lock); the ssh update of two lanes never shares its scratch worktree (flock on <queue>.ssh-lock). A push to main runs the light steps and every container-free
unit test (ci.yml), the nightly the full ci: together they are the alarm for a merge whose tree no full run tested exactly as merged.

Gap scan. Every 30 minutes the runner looks at all open pull requests, whether or not the queue is empty (scripts/merge/gapscan.py): conflict, red CI, approved but not queued (a comment `调度批准入队 head <sha>`), no commit or comment for an hour.
It cannot message a session: a GAPS line in the log and <queue dir>/gaps.md say who owns what (branch [claude/]<session>-... -> session, names from policy.json sessions.config), the session watching the log forwards it. The same pull request and reason is reported once in 4 hours.

Drift check. At start and every 24 hours the runner runs scripts/merge/driftscan.py (report only): dropped pull requests, worktree markers, a runner older than main, absent card owners, a stale lead handoff.
One DRIFT line in the log when there are new findings, the full list in <queue dir>/drift.md; the same finding is reported once in 24 hours. Run `driftscan.py` by hand for the list now.

Queue file: one pull request number per line, optionally "<n> after=<m>[,<m>...]" (`mq.py enqueue <n> --after <m>`: n is not started until every m is MERGED, whatever lane either is in; WAIT-DEP in the log
while it waits and again every hour, HOLD when a dependency was closed unmerged;
`once <n>` does not look at dependencies; after deploying this version restart the running runner first: an older runner does not understand "after=" lines and never starts them, and its enqueue would drop the dependency; a dependency cycle is refused at enqueue), anything else is kept as it is. Every change goes through flock on <queue>.lock and an atomic replace (temporary file, then rename), so an enqueue can no longer be
lost to the runner removing a finished entry (a raw `echo n >> merge.queue` or `sed -i` outside this script is not safe). Log: one line per event,
"<ISO time> <EVENT> pr=#<n> run=<ci run id or -> elapsed=<seconds since the pull request was taken>s [lane=code|docs] <detail>" (lane= once the lane of the pull request is known), appended to <queue dir>/merge.log and echoed. A failing runner step is logged as ERROR and retried
with a growing pause; the third failure in a row puts the pull request on HOLD and takes it out of the queue.
"""
import argparse
import contextlib
import fcntl
import os
import re
import sys
import tempfile
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import driftscan   # noqa: E402
import gapscan   # noqa: E402
import issue_gate   # noqa: E402  (one title check for post_review and the merge)
import mq_acceptance, mq_title   # noqa: E402  (the acceptance guard and the title check at enqueue)
import post_review   # noqa: E402  (the machine-read header of a review: one parser for the poster and the queue)
from mq_github import Gh, SignalError, failure_detail, sh, update_over_ssh   # noqa: E402,F401  (the GitHub and git side)
from mq_queue import LINE, Log, Queue   # noqa: E402,F401  (the queue file and the log)
REQUIRED = ("backend verify", "frontend verify", "secret scan")
URGENT_LABEL = "急"   # put on by the coordinator: the pull request is picked before the others of its lane and the queue never cancels its CI runs
ENV_STEPS = ("docker is running", "test images are present")   # exact names in ci.yml of the steps that go red when the machine's container runtime is down
OK = ("pass", "skipping")
LANES = ("code", "docs")
FAILING = ("failure", "timed_out", "startup_failure")


class Cancelled(Exception):
    """`mq.py remove --cancel` asked the worker to stop; raised at a checkpoint before the merge is made."""


def run_id(link):
    m = re.search(r"runs/(\d+)", link or "")
    return m.group(1) if m else None


def red_checks(checks):
    """Checks that are not pass or skipping, except a light check whose full-named twin is listed: the light run that the label's own run cancelled is no verdict."""
    full = {c["name"] for c in checks if not c["name"].endswith("(light)")}
    return [c for c in checks if c["bucket"] not in OK and not (c["name"].endswith("(light)") and c["name"][:-len("(light)")].rstrip() in full)]


LEAD = "one-lead"   # the session that posts approvals; it never releases its own pull request (rules ⑤, #856)


def release_verdict(reviews, comments, head, patch_id_of, approvals_count=True):
    """(True, why) when the change at `head` may be merged, (False, why) when the newest statement for it is a 必改, (None, why) when nothing speaks for it. The newest statement that speaks for `head` decides: a first-round review (通过 or 必改), a 必改 of a final review,
    or a dispatcher approval comment `调度批准入队 head <sha>` (gapscan.APPROVAL; how the coordinator lets a 必改 fix through, RULES ⑤), which counts as 通过.
    Every session posts through the same GitHub account, so who wrote an approval cannot be told apart: approvals count only on a branch that names a session of sessions.config other than
    lead (Processor.released); approvals_count=False (lead's branch, an old or unknown branch name, or no branch) ignores them, and only a first-round review can release it (#856).
    A review speaks for `head` when GitHub holds it on the commit its header names and that commit is `head` or has the same patch-id as `head`; an approval when it names `head` or a commit with the same patch-id
    (a rebase, or the queue's own merge of main, that leaves the change as it was). The patch-ids are git's (`patch_id_of(commit)`, None when git cannot say), never the one a review body states."""
    fields = post_review.load_policy()[0]["header_fields"]
    head_id = None
    said = []

    def same(commit):
        nonlocal head_id
        if head.startswith(commit):
            return True
        head_id = head_id or patch_id_of(head)
        return head_id is not None and patch_id_of(commit) == head_id
    for r in reviews:
        h = post_review.parse_header(r["body"], fields)
        if h and r["commit"] == h["commit"] and not (h["kind"] == post_review.FINAL and h["verdict"] == post_review.PASS) and same(h["commit"]):
            said.append((r["at"], h["verdict"], f"{h['reviewer']} on {h['commit'][:10]}"))
    for c in comments:
        m = gapscan.APPROVAL.search(c["body"]) if approvals_count and c["author"] == gapscan.APPROVER else None
        if m and same(m.group(1).lower()):
            said.append((c["at"], post_review.PASS, f"approval of {m.group(1)[:10]}"))
    if not said:
        return None, f"no first-round review or approval speaks for head {head[:10]} (none on it, none on a commit with its patch-id {head_id or patch_id_of(head) or 'unknown'})"
    at, verdict, who = max(said)
    return verdict == post_review.PASS, f"newest: {verdict} ({who}, {at})"


def full_named(check):
    """A check of a full (non-light) run. A label other than `queued` starts a run whose jobs all skip, and GitHub lists those checks
    under the unexpanded name template (`...${{ ... }}`): never a verdict, never the full check (#808)."""
    return not check["name"].endswith("(light)") and "${{" not in check["name"]


def required_state(checks):
    """(ok, problems): the three required checks are listed (never with the " (light)" suffix) and every one of them is `pass`."""
    problems = []
    for prefix in REQUIRED:
        found = [c for c in checks if c["name"].startswith(prefix) and full_named(c)]
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
    """'docs' when every path of the pull request is docs (docs_only) and none is guarded (policy.json review.final_paths), else 'code'; a lookup that fails is 'code' (the stricter lane).
    The docs lane is the fast lane: its own worker, serial within the lane, CI without the heavy steps (tier=docs), still checked against the current main under the merge lock."""
    check = docs_only(repo)

    def lane(gh, n):
        files = gh.files(n)
        return "docs" if check(files) and not any(f.startswith(guarded_paths()) for f in files) else "code"
    return lane


def guarded_paths():
    return tuple(post_review.load_policy()[0]["final_paths"])


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
        if files is not None and self.docs_only(files):
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
        """A pull request GitHub reports as conflicting: the update merges main into it.
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
        self.log.event("SSH-UPDATE" if res == "ok" else f"SSH-UPDATE-{res.upper()}", n, None, since, "local merge pushed over ssh")
        return res

    def wait_container_runtime(self, n, minutes=30):
        """True once `docker info` answers (polled every minute); False when it did not within `minutes`."""
        for _ in range(minutes):
            self.checkpoint(n)
            if self.gh.docker_ok():
                return True
            self.sleep(60)
        return False

    def await_new_head(self, n, v, since):
        """After an update: the view of the pull request once its head differs from the old one (GitHub takes a while to show the push); the last view when it never did."""
        for _ in range(8):
            now = self.gh.view(n) or v
            if now["head"] != v["head"]:
                return now
            self.sleep(15)
        self.log.event("NEW-HEAD-NOT-SEEN", n, None, since, "the head did not change after the update; going on with the head as it is")
        return self.gh.view(n) or v

    def await_new_ci(self, n, v, since):
        """True once a ci run exists for the head of v (polled up to 5 minutes); the checks listed before that belong to an older head."""
        for _ in range(20):
            self.checkpoint(n)
            if self.gh.ci_started(v["branch"], v["head"]):
                return True
            self.sleep(15)
        self.log.event("NEW-CI-NOT-SEEN", n, None, since, "no CI run was seen on the updated head within 5 minutes; going on")
        return False

    def wait_checks(self, n, since):
        """Checks once none is pending; None when they stayed pending past the CI timeout. A failed gh call is no answer: asked again."""
        for _ in range(20):
            self.checkpoint(n)   # until the full-named backend check is listed: right after the label only the light run's checks may be there
            listed = self.gh.checks(n) or []
            if any(c["name"].startswith("backend verify") and full_named(c) for c in listed):
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
            owner = gapscan.author_session(v["branch"], gapscan.load_owners())
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
        attempt, moved, refused, unseen, env_failures, looked = 1, 0, 0, 0, 0, False   # attempts count red checks and a head that stopped being green; moved: main advanced during the full CI; refused: the merge itself was refused (own counter, growing pause)
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
            if not looked:   # once, before any CI is spent: the same check as under the merge lock
                looked = True
                ok, why = self.released(n, v["head"])
                if not ok:
                    return self.hold(n, None, since, f"not released for merge: {why}")
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
                v = self.await_new_head(n, v, since)
                new_ci_seen = self.await_new_ci(n, v, since)
            else:
                new_ci_seen = True   # no update this round: the CI that runs is the one of the head as it is
            self.checkpoint(n)
            self.own_head[n] = v["head"]
            self.queue.set_head(n, v["head"])   # kept on disk too: a runner restarted in the middle must not take the queue's own update for the author's push
            if "queued" not in v["labels"]:
                if self.gh.label(n, "queued", True):
                    self.log.event("LABEL-QUEUED", n, None, since)
                self.sleep(20)   # the run the label starts must be listed before the checks are read: until then the old green run of the same names is all there is (#977)
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
                # an environment failure: every red run failed only in environment steps, and the container runtime really is down (a wrong test-images.txt turns the same step red with docker fine)
                env_runs = runs if runs and all(steps[r] and all(name in ENV_STEPS for name in steps[r]) for r in runs) and not self.gh.docker_ok() else []
                if env_runs:   # the machine, not the pull request: wait for the container runtime, run again, and the author's attempt is not used up
                    env_failures += 1
                    self.log.event("ENV", n, env_runs[0], since, f"{', '.join(c['name'] for c in red)}: the environment failed, not the pull request (#{env_failures}); waiting for the container runtime")
                    if env_failures > 3 or not self.wait_container_runtime(n):
                        return self.hold(n, ",".join(env_runs), since, "the environment did not recover (container runtime still down): not the author's failure")
                    for r in runs:
                        self.gh.rerun(r)
                    self.sleep(30)
                    continue
                if attempt == 1:
                    self.log.event("RERUN-FAILED", n, runs[0] if runs else None, since, ", ".join(f"{c['name']}={c['bucket']}" for c in red) + failure_detail(self.gh, runs, steps))   # before the rerun: gh gives no failed log of a run that is running again
                    for r in runs:
                        self.gh.rerun(r)
                    self.sleep(30)
                    attempt += 1
                    continue
                return self.hold(n, ",".join(runs), since, "; ".join(f"{c['name']}={c['bucket']}" for c in red) + failure_detail(self.gh, runs, steps))
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
            if result == "moved" and not new_ci_seen:   # nothing ran on the new head: the verdict was read off the old one, that is no main moving
                unseen += 1
                self.log.event("MOVED-NOT-COUNTED", n, None, since, "no CI run was seen on the updated head: not counted as main moving")
                if unseen > 6:
                    return self.hold(n, None, since, "the CI of the updated head never started")
                self.sleep(30)
                continue
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
                if refused < 4 and (self.gh.view(n) or {}).get("merge") == "BLOCKED":   # a label added after `queued` starts an all-skipped run that hides the green required checks (#977): take `queued` off, the next round puts it back and so starts a run of its own
                    self.gh.label(n, "queued", False)
                    self.log.event("RELABEL", n, None, since, "merge blocked with the checks green: `queued` taken off and put back")
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

    def released(self, n, head):
        """release_verdict for `head`, from the pull request's reviews and comments as gh lists them now. A docs-lane pull request (tier=docs) needs no review unless it touches a guarded path
        (policy.json review.final_paths); a file list gh cannot give is no exemption."""
        if self.log.lanes.get(n) == "docs":
            files = self.gh.files(n)
            if files and not any(f.startswith(guarded_paths()) for f in files):
                return True, "docs lane, no guarded path: no review needed"
        reviews, comments = self.gh.reviews(n), self.gh.comments(n)
        if reviews is None or comments is None:
            return False, "gh could not list the reviews and comments"
        known = {}

        def patch_id_of(commit):
            if commit not in known:
                known[commit] = self.gh.patch_id(self.repo, n, commit)
            return known[commit]
        view = self.gh.view(n)
        branch = view.get("branch") if isinstance(view, dict) else None
        owners = gapscan.load_owners()
        owner = gapscan.author_session(branch, owners) if branch else None
        own = owner not in owners or owner == LEAD   # approvals count only on a branch of a known session other than lead
        ok, why = release_verdict(reviews, comments, head, patch_id_of, approvals_count=not own)
        if own and ok is not True:
            why += (f"; approvals do not count on lead's own pull request, nor on one whose branch names no session of sessions.config "
                    f"({branch or 'branch unknown'}): a first-round review must pass")
        commit = head
        for _ in range(10):   # nothing speaks for the head: a merge of main into a released head (the queue's own update) carries its verdict; merging main may change the context lines, and so the patch-id
            if ok is not None:
                break
            commit = self.gh.main_merge_parent(self.repo, n, commit)
            if not commit:
                break
            ok, carried = release_verdict(reviews, comments, commit, patch_id_of, approvals_count=not own)
            if ok is not None:
                why = f"{carried}; carried across the merge of main into {commit[:10]}"
        return ok is True, why

    def read_back(self, n, head, since):
        """After the merge call: GitHub must show the pull request MERGED, with the pinned head as the second parent of the merge commit. Anything else is an ALERT and both lanes pause (`mq.py resume`); the main checkout is not fast-forwarded then."""
        for attempt in range(3):   # the state may lag a moment behind the merge call
            if attempt:
                self.sleep(5)
            seen = self.gh.readback(n)
            if seen and seen[0] == "MERGED" and len(seen[1]) == 2 and seen[1][1] == head:
                return True
        why = "gh could not read the pull request back" if seen is None else f"state {seen[0]}, merge commit parents {[p[:10] for p in seen[1]] or 'none'}, expected MERGED with second parent {head[:10]}"
        self.log.event("ALERT", n, None, since, f"READBACK: {why}; both lanes pause for `mq.py resume`")
        self.pause.hold(f"READBACK #{n}: {why}")
        return False

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
            ok, why = self.released(n, head)
            if not ok:
                return self.hold(n, None, since, f"not released for merge: {why}")
            if (why := issue_gate.title_problem(v["title"], v["body"])) is not None:   # edited after the review: caught here
                return self.hold(n, None, since, f"title: {why}")
            self.checkpoint(n)   # the last one: under the merge lock, right before gh is asked
            ok, message = self.gh.merge(n, head)
            if ok:
                self.log.event("MERGED", n, run_id(checks[0].get("link")), since, head[:10])
        if ok:
            if self.read_back(n, head, since):
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


def run_drift(proc, directory, every=driftscan.EVERY, first=30, stop=lambda: False, src=None):
    """The drift check thread: first after `first` seconds (runner start), then every `every`. Nothing a check raises ends the thread."""
    wait = first
    while not stop():
        proc.sleep(wait)
        if stop():
            break
        wait = every
        try:
            driftscan.scan(src or driftscan.Live(), proc.queue, directory, proc.log)
        except (Exception, SystemExit) as e:
            proc.log.event("ERROR", None, None, None, f"drift check: {type(e).__name__}: {e}")


def run_loop(proc, idle=60, stop=lambda: False, directory=None):
    """Both lanes, each in its own thread, until stop(); with a queue directory also the gap scan."""
    threads = [threading.Thread(target=run_lane, args=(proc, lane, idle, stop), name=f"mq-{lane}", daemon=True) for lane in LANES]
    if directory:
        threads.append(threading.Thread(target=run_scan, args=(proc, directory), kwargs=dict(stop=stop), name="mq-gap-scan", daemon=True))
        threads.append(threading.Thread(target=run_drift, args=(proc, directory), kwargs=dict(stop=stop), name="mq-drift", daemon=True))
    for t in threads:
        t.start()
    for t in threads:
        t.join()


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
    """Only a note before the pull request waits."""
    try:
        gh = Gh()
        for n in numbers:
            v = gh.view(n)
            if v and v["state"] == "OPEN" and v["merge"] == "DIRTY":
                print(f"#{n} conflicts with main: the author has to merge main", file=sys.stderr)
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
        mq_title.enforce(gh := Gh(), a.prs); mq_acceptance.enforce(repo_root(), gh, a.prs)
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
