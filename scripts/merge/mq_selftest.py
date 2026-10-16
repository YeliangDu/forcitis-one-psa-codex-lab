"""Self-test of mq.py (fake gh, real git for the ssh update, real processes for the queue lock). Not run on its own: `mq.py --self-test`."""
import os
import re
import subprocess
import sys
import tempfile

LOG_LINE = re.compile(r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d[+-]\d\d:\d\d [A-Z-]+ pr=#(\d+|-) run=(\d+|-) elapsed=\d+s( .*)?$")
HEAVY = "backend verify (Modulith + ArchUnit guardrails + integration tests)", "frontend verify (typecheck + lint guardrails F1-F14 + structure + build)"


def link(run):
    return f"https://github.com/o/r/actions/runs/{run}/job/1"


def green(run=100, light=False):
    suffix = " (light)" if light else ""
    return [{"name": n + suffix, "bucket": "pass", "link": link(run)} for n in HEAVY] + [{"name": "secret scan (gitleaks)", "bucket": "pass", "link": link(run)}]


def red(run=100, bucket="fail"):
    return [{"name": HEAVY[0], "bucket": bucket, "link": link(run)}, {"name": HEAVY[1], "bucket": "pass", "link": link(run)}]


class FakeGh:
    def __init__(self):
        self.prs, self.calls, self.runs = {}, [], {}

    def add(self, n, merge="CLEAN", labels=(), rounds=None, update_ok=True, merge_results=(True,)):
        self.prs[n] = dict(state="OPEN", merge=merge, labels=set(labels), branch=f"b{n}", rounds=rounds or [dict(pending=0, checks=green())], round=0, pend=None,
                           update_ok=update_ok, merge_results=list(merge_results))

    def view(self, n):
        p = self.prs[n]
        return {"state": p["state"], "merge": p["merge"], "labels": sorted(p["labels"]), "branch": p["branch"]}

    def checks(self, n):
        p = self.prs[n]
        r = p["rounds"][min(p["round"], len(p["rounds"]) - 1)]
        if p["pend"] is None:
            p["pend"] = r["pending"]
        if p["pend"] > 0:
            p["pend"] -= 1
            return [dict(c, bucket="pending") for c in r["checks"]]
        return r["checks"]

    def update_branch(self, n):
        self.calls.append(("update-branch", n))
        p = self.prs[n]
        if p["update_ok"]:
            p["merge"] = "CLEAN"
        return p["update_ok"]

    def label(self, n, name, add):
        self.calls.append(("label", n, name, add))
        (self.prs[n]["labels"].add if add else self.prs[n]["labels"].discard)(name)
        return True

    def active_runs(self, branch):
        return list(self.runs.get(branch, []))

    def cancel_run(self, run):
        self.calls.append(("cancel", run))
        for k in self.runs:
            if run in self.runs[k]:
                self.runs[k].remove(run)
        return True

    def rerun(self, run):
        self.calls.append(("rerun", run))
        for p in self.prs.values():
            p["round"] += 1
            p["pend"] = None
        return True

    def merge(self, n):
        p = self.prs[n]
        ok = p["merge_results"].pop(0) if p["merge_results"] else True
        self.calls.append(("merge", n, ok))
        if ok:
            p["state"] = "MERGED"
        return ok


def run(mq):
    env = {"GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_SYSTEM": "/dev/null"}
    old = {k: os.environ.get(k) for k in env}
    os.environ.update(env)
    try:
        with tempfile.TemporaryDirectory() as d:
            _scenarios(mq, d)
            _ssh_update(mq, d)
            _queue_lock(mq, d)
            _train(mq)
    finally:
        for k, v in old.items():
            os.environ.pop(k, None) if v is None else os.environ.__setitem__(k, v)


def _proc(mq, d, name, gh, queue_numbers=(), **kw):
    q = mq.Queue(os.path.join(d, name, "merge.queue"))
    q.enqueue(*queue_numbers)
    log = mq.Log(os.path.join(d, name, "merge.log"), echo=False)
    slept = []
    updater = kw.pop("updater", lambda repo, branch, wt: "ok")
    return mq.Processor(gh, q, log, "/repo", sleep=slept.append, updater=updater, **kw), q, log, slept


def _scenarios(mq, d):
    def events(log):
        return [l.split()[1] for l in log.lines]

    # clean path: behind -> gh update-branch -> label -> checks pass -> merge; the other queued pull request's CI is cancelled, once
    gh = FakeGh(); gh.add(1, merge="BEHIND"); gh.add(2); gh.runs["b2"] = [555, 556]
    proc, q, log, _ = _proc(mq, d, "a", gh, [1, 2])
    assert proc.process(1) == "MERGED"
    assert events(log) == ["START", "CANCEL-RUN", "CANCEL-RUN", "UPDATE", "LABEL-QUEUED", "MERGED"], events(log)
    assert ("cancel", 555) in gh.calls and ("cancel", 556) in gh.calls and ("update-branch", 1) in gh.calls and gh.calls[-1] == ("merge", 1, True)
    assert all(LOG_LINE.match(l) for l in log.lines), log.lines
    assert "pr=#2 run=555" in log.lines[1] and log.lines[-1].split()[3] == "run=100", log.lines
    assert open(os.path.join(d, "a", "merge.log")).read().splitlines() == log.lines

    # gh cannot update (workflow scope): the local ssh update runs; a text conflict there ends in CONFLICT
    gh = FakeGh(); gh.add(3, merge="BEHIND", update_ok=False)
    seen = []
    proc, q, log, _ = _proc(mq, d, "b", gh, [3], updater=lambda repo, branch, wt: (seen.append((repo, branch)), gh.prs[3].update(merge="CLEAN"), "ok")[2])
    assert proc.process(3) == "MERGED" and seen == [("/repo", "b3")] and "SSH-UPDATE" in events(log) and "UPDATE" not in events(log), events(log)
    gh = FakeGh(); gh.add(4, merge="BEHIND", update_ok=False)
    proc, q, log, _ = _proc(mq, d, "b2", gh, [4], updater=lambda repo, branch, wt: (gh.prs[4].update(merge="DIRTY"), "conflict")[1])
    assert proc.process(4) == "CONFLICT" and "SSH-UPDATE-CONFLICT" in events(log) and not any(c[0] == "merge" for c in gh.calls)

    # conflict: nothing is merged or labelled
    gh = FakeGh(); gh.add(5, merge="DIRTY")
    proc, q, log, _ = _proc(mq, d, "c", gh, [5])
    assert proc.process(5) == "CONFLICT" and events(log) == ["START", "CONFLICT"] and gh.calls == []

    # a failed check is re-run once and then merged; the log names the failed check and the run
    gh = FakeGh(); gh.add(6, rounds=[dict(pending=1, checks=red(777)), dict(pending=0, checks=green(778))])
    proc, q, log, slept = _proc(mq, d, "d", gh, [6])
    assert proc.process(6) == "MERGED" and ("rerun", "777") in gh.calls and events(log).count("RERUN-FAILED") == 1, events(log)
    assert any("RERUN-FAILED pr=#6 run=777" in l and "fail" in l for l in log.lines) and log.lines[-1].split()[3] == "run=778"

    # still red after the one re-run: HOLD, no second re-run, never merged
    gh = FakeGh(); gh.add(7, rounds=[dict(pending=0, checks=red(1)), dict(pending=0, checks=red(2))])
    proc, q, log, _ = _proc(mq, d, "e", gh, [7])
    assert proc.process(7) == "HOLD" and [c for c in gh.calls if c[0] == "rerun"] == [("rerun", "1")] and not any(c[0] == "merge" for c in gh.calls)
    assert any(l.split()[1] == "HOLD" and "run=2" in l for l in log.lines), log.lines
    # a cancelled check counts as red too
    gh = FakeGh(); gh.add(8, rounds=[dict(pending=0, checks=red(1, "cancel")), dict(pending=0, checks=green(9))])
    proc, q, log, _ = _proc(mq, d, "e2", gh, [8])
    assert proc.process(8) == "MERGED" and ("rerun", "1") in gh.calls

    # while the head waits (pending), the others' runs are cancelled again and again, as new ones appear
    gh = FakeGh(); gh.add(9, rounds=[dict(pending=6, checks=green())]); gh.add(10); gh.runs["b10"] = [900]
    proc, q, log, slept = _proc(mq, d, "f", gh, [9, 10])
    orig = gh.checks
    def checks_with_new_runs(n, calls=[0]):
        calls[0] += 1
        if calls[0] == 3:
            gh.runs["b10"] = [901]
        return orig(n)
    gh.checks = checks_with_new_runs
    assert proc.process(9) == "MERGED" and ("cancel", 900) in gh.calls and ("cancel", 901) in gh.calls, gh.calls

    # light-only green: relabelled, not merged; the next round with the heavy checks merges
    gh = FakeGh(); gh.add(11, rounds=[dict(pending=0, checks=green(light=True)), dict(pending=0, checks=green())])
    p11 = gh.prs[11]
    orig_label = gh.label
    def label(n, name, add):
        r = orig_label(n, name, add)
        if not add:
            p11["round"] = 1
        return r
    gh.label = label
    proc, q, log, _ = _proc(mq, d, "g", gh, [11])
    assert proc.process(11) == "MERGED" and "LIGHT-ONLY" in events(log) and ("label", 11, "queued", False) in gh.calls and events(log).index("LIGHT-ONLY") < events(log).index("MERGED")
    gh = FakeGh(); gh.add(12, rounds=[dict(pending=0, checks=green(light=True))])
    proc, q, log, _ = _proc(mq, d, "g2", gh, [12])
    assert proc.process(12) == "HOLD" and events(log).count("LIGHT-ONLY") == 4 and not any(c[0] == "merge" for c in gh.calls), "light CI alone is never enough"

    # CI that stays pending past the timeout is a HOLD
    gh = FakeGh(); gh.add(13, rounds=[dict(pending=10 ** 6, checks=green())])
    proc, q, log, slept = _proc(mq, d, "h", gh, [13], ci_timeout=120)
    assert proc.process(13) == "HOLD" and any("pending after 2 minutes" in l for l in log.lines) and sum(slept) < 400

    # merge refused twice, accepted the third time
    gh = FakeGh(); gh.add(14, merge_results=(False, False, True))
    proc, q, log, _ = _proc(mq, d, "i", gh, [14])
    assert proc.process(14) == "MERGED" and events(log).count("RETRY-MERGE") == 2

    # the runner: queue order, each head processed then removed, DEQUEUE logged; the queue is read again after every PR
    gh = FakeGh(); gh.add(15, merge="DIRTY"); gh.add(16); gh.add(17)
    proc, q, log, slept = _proc(mq, d, "j", gh, [15, 16])
    order = []
    real = proc.process
    def spy(n):
        order.append(n)
        if n == 15:
            q.enqueue(17)
        return real(n)
    proc.process = spy
    mq.run_loop(proc, stop=lambda: len(order) == 3)
    assert order == [15, 16, 17] and q.numbers() == [] and events(log).count("DEQUEUE") == 3 and all(LOG_LINE.match(l) for l in log.lines)


def _ssh_update(mq, d):
    def g(cwd, *a):
        r = subprocess.run(["git", *a], cwd=cwd, capture_output=True, text=True)
        assert r.returncode == 0, (a, r.stderr)
        return r.stdout.strip()

    origin, work = os.path.join(d, "o.git"), os.path.join(d, "w")
    g(d, "init", "-q", "--bare", "-b", "main", origin)
    g(d, "clone", "-q", origin, work)
    g(work, "config", "commit.gpgsign", "false")

    def commit(path, text, msg):
        open(os.path.join(work, path), "w").write(text)
        g(work, "add", path); g(work, "commit", "-q", "-m", msg)

    commit("a.txt", "a\n", "base"); g(work, "push", "-q", "origin", "main")
    for name, (path, text) in {"pr-clean": ("b.txt", "b\n"), "pr-conflict": ("a.txt", "mine\n")}.items():
        g(work, "checkout", "-q", "-B", name, "main"); commit(path, text, name); g(work, "push", "-q", "origin", name)
    g(work, "checkout", "-q", "main"); commit("a.txt", "main moved\n", "main moves"); g(work, "push", "-q", "origin", "main")
    wt = os.path.join(d, "mq-wt")
    assert mq.update_over_ssh(work, "pr-clean", wt) == "ok"
    g(work, "fetch", "-q", "origin")
    assert g(work, "merge-base", "--is-ancestor", "origin/main", "origin/pr-clean") == "" and not os.path.exists(wt) and "mq-wt" not in g(work, "worktree", "list")
    before = g(work, "rev-parse", "origin/pr-conflict")
    assert mq.update_over_ssh(work, "pr-conflict", wt) == "conflict"
    g(work, "fetch", "-q", "origin")
    assert g(work, "rev-parse", "origin/pr-conflict") == before and not os.path.exists(wt) and "mq-wt" not in g(work, "worktree", "list")
    assert mq.update_over_ssh(work, "no-such-branch", wt) == "error"


def _queue_lock(mq, d):
    qpath = os.path.join(d, "lock", "merge.queue")
    q = mq.Queue(qpath)
    q.enqueue(*range(100, 130))
    me = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mq.py")
    procs = [subprocess.Popen([sys.executable, me, "--queue", qpath, "enqueue", str(n)], stdout=subprocess.DEVNULL) for n in range(1, 31)]
    procs += [subprocess.Popen([sys.executable, me, "--queue", qpath, "remove", str(n)], stdout=subprocess.DEVNULL) for n in range(100, 130)]
    assert all(p.wait() == 0 for p in procs)
    assert sorted(q.numbers()) == list(range(1, 31)), "no enqueue lost to a concurrent remove"
    assert not [f for f in os.listdir(os.path.dirname(qpath)) if f.startswith(".queue-")], "no temporary file left"
    q.enqueue(5, 6)
    assert q.numbers().count(5) == 1
    q.before(30, 1)
    assert q.numbers()[0] == 30 and q.numbers().count(30) == 1
    q.before(99, 7777)
    assert q.numbers()[-1] == 99
    open(qpath, "a").write("# note\n")
    q.remove(99); q.enqueue(98)
    assert "# note" in open(qpath).read() and q.numbers()[-1] == 98


def _train(mq):
    r = subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "mq.py"), "train", "--self-test"], capture_output=True, text=True)
    assert r.returncode == 0 and "merge_train self-test: ok" in r.stdout, (r.stdout, r.stderr[-300:])
