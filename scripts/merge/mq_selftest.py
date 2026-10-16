"""Self-test of mq.py (fake gh, real git for the ssh update, real processes and threads for the queue lock and the lanes). Not run on its own: `mq.py --self-test`."""
import os
import re
import subprocess
import sys
import tempfile
import threading
import time

LOG_LINE = re.compile(r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d[+-]\d\d:\d\d [A-Z-]+ pr=#(\d+|-) run=(\d+|-) elapsed=\d+s( .*)?$")
HEAVY = "backend verify (Modulith + ArchUnit guardrails + integration tests)", "frontend verify (typecheck + lint guardrails F1-F14 + structure + build)"
SECRET = "secret scan (gitleaks)"


def link(run):
    return f"https://github.com/o/r/actions/runs/{run}/job/1"


def green(run=100, light=False):
    suffix = " (light)" if light else ""
    return [{"name": n + suffix, "bucket": "pass", "link": link(run)} for n in HEAVY] + [{"name": SECRET, "bucket": "pass", "link": link(run)}]


def red(run=100, bucket="fail"):
    return [{"name": HEAVY[0], "bucket": bucket, "link": link(run)}, {"name": HEAVY[1], "bucket": "pass", "link": link(run)}, {"name": SECRET, "bucket": "pass", "link": link(run)}]


class FakeGh:
    def __init__(self):
        self.prs, self.calls, self.runs, self.main_runs, self.lock, self.slow = {}, [], {}, {"push": None, "schedule": None}, threading.RLock(), False

    def add(self, n, merge="CLEAN", labels=(), rounds=None, update_ok=True, merge_results=((True, ""),), behind=0, state="OPEN"):
        self.prs[n] = dict(state=state, merge=merge, labels=set(labels), branch=f"b{n}", head=f"{n:040x}", behind=behind, rounds=rounds or [dict(pending=0, checks=green())], round=0, pend=None,
                           update_ok=update_ok, merge_results=list(merge_results))

    def view(self, n):
        p = self.prs[n]
        return {"state": p["state"], "merge": p["merge"], "labels": sorted(p["labels"]), "branch": p["branch"], "head": p["head"]}

    def behind_by(self, n, head):
        value = self.prs[n]["behind"]
        if self.slow:
            time.sleep(0.02)   # widens the window between looking and merging, so that a missing merge lock shows
        return value

    def checks(self, n):
        with self.lock:
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
            p["merge"], p["behind"], p["head"] = "CLEAN", 0, p["head"][:-1] + "u"
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

    def main_run(self, event):
        value = self.main_runs[event]
        if isinstance(value, Exception):
            raise value
        return value

    def merge(self, n, head):
        with self.lock:
            p = self.prs[n]
            ok, msg = p["merge_results"].pop(0) if p["merge_results"] else (True, "")
            if head != p["head"]:
                ok, msg = False, "head commit does not match"
            self.calls.append(("merge", n, ok, head))
            self.merged_behind = getattr(self, "merged_behind", []) + ([p["behind"]] if ok else [])
            if ok:
                p["state"] = "MERGED"
                for o, q in self.prs.items():
                    if o != n and q["state"] == "OPEN":
                        q["behind"] += 1   # main moved
            return ok, msg


def run(mq):
    env = {"GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_SYSTEM": "/dev/null"}
    old = {k: os.environ.get(k) for k in env}
    os.environ.update(env)
    try:
        with tempfile.TemporaryDirectory() as d:
            _required(mq)
            _scenarios(mq, d)
            _merge_guards(mq, d)
            _pause(mq, d)
            _signals(mq, d)
            _gh_commands(mq, d)
            _lanes(mq, d)
            _runner(mq, d)
            _ssh_update(mq, d)
            _queue_lock(mq, d)
            _train(mq)
    finally:
        for k, v in old.items():
            os.environ.pop(k, None) if v is None else os.environ.__setitem__(k, v)


def _proc(mq, d, name, gh, queue_numbers=(), lane=None, on_sleep=None, **kw):
    q = mq.Queue(os.path.join(d, name, "merge.queue"))
    q.enqueue(*queue_numbers)
    log = mq.Log(os.path.join(d, name, "merge.log"), echo=False)
    slept = []

    def sleep(seconds):
        slept.append(seconds)
        if on_sleep:
            on_sleep(seconds)
    updater = kw.pop("updater", lambda repo, branch, wt: "ok")
    return mq.Processor(gh, q, log, "/repo", sleep=sleep, updater=updater, lane=lane or (lambda g, n: "code"), **kw), q, log, slept


def events(log):
    return [l.split()[1] for l in log.lines]


def _required(mq):
    ok, why = mq.required_state(green())
    assert ok and not why
    assert not mq.required_state(green(light=True))[0], "the light suffix is not the required check"
    assert not mq.required_state([c for c in green() if c["name"] != SECRET])[0]
    assert "secret scan: not listed" in mq.required_state([c for c in green() if c["name"] != SECRET])[1]
    assert not mq.required_state([dict(c, bucket="skipping") for c in green()])[0], "skipping is not pass"
    assert not mq.required_state([dict(c, bucket="pending") if c["name"].startswith("backend") else c for c in green()])[0], "a head still running is not green"
    assert not mq.required_state([])[0]
    assert mq.required_state(green() + [{"name": "assertion drift (warning only)", "bucket": "fail", "link": ""}])[0], "only the three required checks decide"
    assert mq.required_state(green(light=True) + green())[0], "light and full next to each other: the full ones count"


def _scenarios(mq, d):
    # clean path: behind main -> gh update-branch -> label -> required checks green -> merge pinned to the head; others' CI cancelled, once
    gh = FakeGh(); gh.add(1, merge="BEHIND", behind=2); gh.add(2); gh.runs["b2"] = [555, 556]
    proc, q, log, _ = _proc(mq, d, "a", gh, [1, 2])
    assert proc.process(1) == "MERGED"
    assert events(log) == ["START", "CANCEL-RUN", "CANCEL-RUN", "UPDATE", "LABEL-QUEUED", "MERGED"], events(log)
    assert ("cancel", 555) in gh.calls and ("cancel", 556) in gh.calls and ("update-branch", 1) in gh.calls
    assert gh.calls[-1] == ("merge", 1, True, f"{1:040x}"[:-1] + "u"), "the merge names the updated head"
    assert all(LOG_LINE.match(l) for l in log.lines), log.lines
    assert "pr=#2 run=555" in log.lines[1] and log.lines[-1].split()[3] == "run=100", log.lines
    assert open(os.path.join(d, "a", "merge.log")).read().splitlines() == log.lines

    # behind by commit count only (strict off: GitHub says CLEAN while the branch is behind): the queue updates it anyway
    gh = FakeGh(); gh.add(3, merge="CLEAN", behind=4)
    proc, q, log, _ = _proc(mq, d, "a2", gh, [3])
    assert proc.process(3) == "MERGED" and "UPDATE" in events(log) and ("update-branch", 3) in gh.calls

    # gh cannot update (workflow scope): the local ssh update runs; a text conflict there ends in CONFLICT, an unpushable update in HOLD
    gh = FakeGh(); gh.add(4, merge="BEHIND", update_ok=False)
    seen = []
    proc, q, log, _ = _proc(mq, d, "b", gh, [4], updater=lambda repo, branch, wt: (seen.append((repo, branch)), gh.prs[4].update(merge="CLEAN", behind=0), "ok")[2])
    assert proc.process(4) == "MERGED" and seen == [("/repo", "b4")] and "SSH-UPDATE" in events(log) and "UPDATE" not in events(log), events(log)
    for result, outcome, event in (("conflict", "CONFLICT", "SSH-UPDATE-CONFLICT"), ("error", "HOLD", "SSH-UPDATE-ERROR")):
        gh = FakeGh(); gh.add(5, merge="BEHIND", update_ok=False)
        proc, q, log, _ = _proc(mq, d, "b2" + result, gh, [5], updater=lambda repo, branch, wt, r=result: r)
        assert proc.process(5) == outcome and event in events(log) and not any(c[0] == "merge" for c in gh.calls), events(log)

    # conflict: nothing is merged or labelled
    gh = FakeGh(); gh.add(6, merge="DIRTY")
    proc, q, log, _ = _proc(mq, d, "c", gh, [6])
    assert proc.process(6) == "CONFLICT" and events(log) == ["START", "CONFLICT"] and gh.calls == []

    # closed, or gh cannot say: HOLD, never merged
    gh = FakeGh(); gh.add(7, state="CLOSED")
    proc, q, log, _ = _proc(mq, d, "c2", gh, [7])
    assert proc.process(7) == "HOLD" and "CLOSED" in log.lines[-1] and gh.calls == []
    gh = FakeGh(); gh.add(8)
    gh.view = lambda n: None
    proc, q, log, _ = _proc(mq, d, "c3", gh, [8])
    assert proc.process(8) == "HOLD" and not any(c[0] == "merge" for c in gh.calls)

    # a failed check is re-run once and then merged; the log names the failed check and the run
    gh = FakeGh(); gh.add(9, rounds=[dict(pending=1, checks=red(777)), dict(pending=0, checks=green(778))])
    proc, q, log, slept = _proc(mq, d, "d", gh, [9])
    assert proc.process(9) == "MERGED" and ("rerun", "777") in gh.calls and events(log).count("RERUN-FAILED") == 1, events(log)
    assert any("RERUN-FAILED pr=#9 run=777" in l and "fail" in l for l in log.lines) and log.lines[-1].split()[3] == "run=778"

    # still red after the one re-run: HOLD, no second re-run, never merged; the run ids of both rounds are in the log (numeric order)
    gh = FakeGh(); gh.add(10, rounds=[dict(pending=0, checks=red(9)), dict(pending=0, checks=red(1000))])
    proc, q, log, _ = _proc(mq, d, "e", gh, [10])
    assert proc.process(10) == "HOLD" and [c for c in gh.calls if c[0] == "rerun"] == [("rerun", "9")] and not any(c[0] == "merge" for c in gh.calls)
    assert any(l.split()[1] == "HOLD" and "run=1000" in l for l in log.lines), log.lines
    gh = FakeGh(); gh.add(11, rounds=[dict(pending=0, checks=red(1, "cancel")), dict(pending=0, checks=green(9))])
    proc, q, log, _ = _proc(mq, d, "e2", gh, [11])
    assert proc.process(11) == "MERGED" and ("rerun", "1") in gh.calls, "a cancelled check counts as red"

    # while the head waits (pending), the others' runs are cancelled again and again, as new ones appear; a pull request the other lane is working on keeps its runs
    gh = FakeGh(); gh.add(12, rounds=[dict(pending=6, checks=green())]); gh.add(13); gh.add(14); gh.runs["b13"] = [900]; gh.runs["b14"] = [950]
    proc, q, log, slept = _proc(mq, d, "f", gh, [12, 13, 14])
    proc.busy.add(14)
    orig = gh.checks
    calls = [0]

    def checks_with_new_runs(n):
        calls[0] += 1
        if calls[0] == 3:
            gh.runs["b13"] = [901]
        return orig(n)
    gh.checks = checks_with_new_runs
    assert proc.process(12) == "MERGED" and ("cancel", 900) in gh.calls and ("cancel", 901) in gh.calls and ("cancel", 950) not in gh.calls, gh.calls

    # light-only green: never merged; relabelled and tried again, HOLD when it stays light
    gh = FakeGh(); gh.add(15, rounds=[dict(pending=0, checks=green(light=True)), dict(pending=0, checks=green())])
    p15 = gh.prs[15]
    orig_label = gh.label

    def label(n, name, add):
        r = orig_label(n, name, add)
        if not add:
            p15["round"] = 1
        return r
    gh.label = label
    proc, q, log, _ = _proc(mq, d, "g", gh, [15])
    assert proc.process(15) == "MERGED" and "REQUIRED-NOT-GREEN" in events(log) and ("label", 15, "queued", False) in gh.calls and events(log).index("REQUIRED-NOT-GREEN") < events(log).index("MERGED")
    gh = FakeGh(); gh.add(16, rounds=[dict(pending=0, checks=green(light=True))])
    proc, q, log, _ = _proc(mq, d, "g2", gh, [16])
    assert proc.process(16) == "HOLD" and events(log).count("REQUIRED-NOT-GREEN") == 4 and not any(c[0] == "merge" for c in gh.calls), "light CI alone is never enough"

    # the three required checks must all be listed and pass: a missing secret scan, an empty list, a backend check that is not pass
    gh = FakeGh(); gh.add(17, rounds=[dict(pending=0, checks=[c for c in green() if c["name"] != SECRET])])
    proc, q, log, _ = _proc(mq, d, "g3", gh, [17])
    assert proc.process(17) == "HOLD" and "secret scan: not listed" in " ".join(log.lines) and not any(c[0] == "merge" for c in gh.calls)
    gh = FakeGh(); gh.add(18, rounds=[dict(pending=0, checks=[])])
    proc, q, log, _ = _proc(mq, d, "g4", gh, [18])
    assert proc.process(18) == "HOLD" and "no check at all" in log.lines[-1] and not any(c[0] == "merge" for c in gh.calls)
    gh = FakeGh(); gh.add(19, rounds=[dict(pending=5, checks=green())])
    proc, q, log, slept = _proc(mq, d, "g5", gh, [19])
    assert proc.process(19) == "MERGED" and slept.count(30) >= 3, "a head whose backend job is still running is waited for, not merged"
    # gh pr checks failing now and then is no answer: asked again
    gh = FakeGh(); gh.add(20)
    real_checks = gh.checks
    flaky = {"n": 0}

    def flaky_checks(n):
        flaky["n"] += 1
        return None if flaky["n"] in (2, 3, 5) else real_checks(n)
    gh.checks = flaky_checks
    proc, q, log, _ = _proc(mq, d, "g6", gh, [20])
    assert proc.process(20) == "MERGED" and flaky["n"] > 5

    # CI that stays pending past the timeout is a HOLD
    gh = FakeGh(); gh.add(21, rounds=[dict(pending=10 ** 6, checks=green())])
    proc, q, log, slept = _proc(mq, d, "h", gh, [21], ci_timeout=120)
    assert proc.process(21) == "HOLD" and any("pending after 2 minutes" in l for l in log.lines) and sum(slept) < 400


def _merge_guards(mq, d):
    # a refused merge is retried with a growing pause and its own counter (the attempts of the checks are not used up), with gh's message and the merge state in the log
    gh = FakeGh(); gh.add(30, rounds=[dict(pending=0, checks=red(5)), dict(pending=0, checks=green(6))],
                          merge_results=((False, "Pull request is not mergeable: required status check expected"), (False, "again"), (False, "again"), (True, "")))
    proc, q, log, slept = _proc(mq, d, "m1", gh, [30])
    assert proc.process(30) == "MERGED" and events(log).count("RETRY-MERGE") == 3 and "RERUN-FAILED" in events(log)
    assert slept[-3:] == [20, 40, 80], slept   # 20 s once, then 40, 80: a growing pause per refusal
    retry = [l for l in log.lines if " RETRY-MERGE " in l][0]
    assert "required status check expected" in retry and "mergeState=CLEAN" in retry, retry
    gh = FakeGh(); gh.add(31, merge_results=((False, "no"),) * 9)
    proc, q, log, _ = _proc(mq, d, "m2", gh, [31])
    assert proc.process(31) == "HOLD" and events(log).count("RETRY-MERGE") == 5 and "refused 5 times" in log.lines[-1]

    # main moved while the full CI ran: the merge waits for an update and a new run; nothing is merged on the stale head
    gh = FakeGh(); gh.add(32)
    proc, q, log, _ = _proc(mq, d, "m3", gh, [32])
    orig = gh.checks
    state = {"n": 0}

    def checks(n):
        state["n"] += 1
        if state["n"] == 2:   # the checks have just ended green: main moves before the merge is looked at
            gh.prs[32]["behind"] = 1
        return orig(n)
    gh.checks = checks
    assert proc.process(32) == "MERGED" and "BEHIND-AT-MERGE" in events(log) and events(log).count("UPDATE") == 1, events(log)
    merges = [c for c in gh.calls if c[0] == "merge"]
    assert len(merges) == 1 and merges[0][3].endswith("u"), "the merge is pinned to the head that contains main"
    gh = FakeGh(); gh.add(33, behind=0)
    proc, q, log, _ = _proc(mq, d, "m4", gh, [33])
    behind_values = iter([0] + [1] * 80)
    gh.behind_by = lambda n, head: next(behind_values)
    assert proc.process(33) == "HOLD" and "main moved" in log.lines[-1] and not any(c[0] == "merge" for c in gh.calls), "main that never stops moving is a HOLD, not an endless loop"

    # the checks are no longer green on this head when the merge is about to happen (a push): not merged, tried again from the checks
    gh = FakeGh(); gh.add(34, rounds=[dict(pending=0, checks=green()), dict(pending=0, checks=green())])
    proc, q, log, _ = _proc(mq, d, "m5", gh, [34])
    orig = gh.checks
    seen = {"n": 0}

    def checks2(n):
        seen["n"] += 1
        return [dict(c, bucket="pending") for c in green()] if seen["n"] == 3 else orig(n)
    gh.checks = checks2
    assert proc.process(34) == "MERGED" and "CHECKS-CHANGED" in events(log) and events(log).index("CHECKS-CHANGED") < events(log).index("MERGED")

    # a head that is not the one the merge names is refused by gh (--match-head-commit): the fake enforces it, the queue passes the head it looked at under the lock
    gh = FakeGh(); gh.add(35)
    proc, q, log, _ = _proc(mq, d, "m6", gh, [35])
    assert proc.process(35) == "MERGED" and gh.calls[-1][3] == gh.prs[35]["head"]


def _pause(mq, d):
    gh = FakeGh(); gh.add(40)
    proc, q, log, slept = _proc(mq, d, "p1", gh, [40])
    pause = proc.pause
    assert pause.check() is False and not pause.is_paused()
    gh.main_runs["push"] = {"id": 9001, "status": "in_progress", "conclusion": None, "sha": "a"}
    gh.main_runs["schedule"] = {"id": 9002, "status": "completed", "conclusion": "cancelled", "sha": "b"}
    assert pause.check() is False, "a run still going or cancelled is not red"
    gh.main_runs["push"] = {"id": 9001, "status": "completed", "conclusion": "failure", "sha": "a"}
    assert pause.check() is True and pause.is_paused() and any(" PAUSED " in l and "push run 9001" in l for l in log.lines)
    assert pause.check() is True and sum(" PAUSED " in l for l in log.lines) == 1, "paused once"
    # both lanes stand still while paused
    stops = iter([False, False, False, True])
    mq.run_lane(proc, "code", stop=lambda: next(stops))
    assert q.numbers() == [40] and ("label", 40, "queued", True) not in gh.calls
    # resume acknowledges the red run it saw; the same red run does not pause again, a new red run (push or nightly) does
    assert pause.resume() == [("push", "9001")] and not pause.is_paused() and pause.check() is False
    gh.main_runs["schedule"] = {"id": 9003, "status": "completed", "conclusion": "failure", "sha": "c"}
    assert pause.check() is True and "nightly run 9003" in open(pause.paused_file).read()
    pause.resume()
    gh.main_runs["push"] = {"id": 9004, "status": "completed", "conclusion": "timed_out", "sha": "d"}
    assert pause.check() is True
    pause.resume()

    # red main appears after the checks were green, just before the merge: the merge waits under the lock until resume, then goes on
    gh = FakeGh(); gh.add(41)
    gh.main_runs["push"] = None
    proc, q, log, slept = _proc(mq, d, "p2", gh, [41])

    def on_sleep(seconds):
        if seconds == 60:
            proc.pause.resume()
    proc.sleep = lambda s: (slept.append(s), on_sleep(s))
    orig = gh.checks
    seen = {"n": 0}

    def checks(n):
        seen["n"] += 1
        if seen["n"] == 2:   # the checks have just ended green; main turns red before the merge is decided
            gh.main_runs["push"] = {"id": 9100, "status": "completed", "conclusion": "failure", "sha": "x"}
        return orig(n)
    gh.checks = checks
    assert proc.process(41) == "MERGED" and "WAIT-PAUSED" in events(log) and "RESUMED" in events(log) and events(log).index("WAIT-PAUSED") < events(log).index("MERGED"), events(log)


def _lanes(mq, d):
    lanes = {50: "code", 51: "docs", 52: "code", 53: "docs"}
    gh = FakeGh()
    for n in lanes:
        gh.add(n)
    proc, q, log, _ = _proc(mq, d, "l1", gh, [50, 51, 52, 53], lane=lambda g, n: lanes[n])
    assert proc.pick("code") == 50 and proc.pick("code") == 52 and proc.pick("code") is None, "a pull request a worker holds is not handed out again"
    assert proc.pick("docs") == 51 and proc.pick("docs") == 53 and proc.pick("docs") is None

    # the lane of a pull request: docs only when relevant_change.py says tier=docs for every path; a lookup that fails is the stricter lane (code)
    with tempfile.TemporaryDirectory() as repo:
        os.makedirs(os.path.join(repo, "scripts/ci"))
        with open(os.path.join(repo, "scripts/ci/relevant_change.py"), "w") as f:
            f.write("import sys\npaths = open(sys.argv[2]).read().split()\nprint('tier=' + ('docs' if all(p.startswith('docs/') for p in paths) else 'code'))\n")

        class Files:
            def __init__(self, files):
                self.f = files

            def files(self, n):
                return self.f
        lane = mq.default_lane(repo)
        assert lane(Files(["docs/a.md", "docs/b/c.md"]), 1) == "docs"
        assert lane(Files(["docs/a.md", "backend/pom.xml"]), 1) == "code"
        assert lane(Files(None), 1) == "code" and lane(Files([]), 1) == "code"
        with open(os.path.join(repo, "scripts/ci/relevant_change.py"), "w") as f:
            f.write("raise SystemExit(3)\n")
        assert mq.default_lane(repo)(Files(["docs/a.md"]), 1) == "code", "the script failing is not docs"
        with open(os.path.join(repo, "scripts/ci/relevant_change.py"), "w") as f:
            f.write("print('tier=docs')\nraise SystemExit(3)\n")
        assert mq.default_lane(repo)(Files(["docs/a.md"]), 1) == "code", "a non-zero exit is never docs, whatever it printed"

    # two lanes at once: both pull requests go through, merges are serialized, the one that merges second finds main moved, updates and merges
    gh = FakeGh(); gh.add(60); gh.add(61)
    gh.slow = True
    lanes = {60: "code", 61: "docs"}
    proc, q, log, _ = _proc(mq, d, "l2", gh, [60, 61], lane=lambda g, n: lanes[n])
    done = []
    stop = lambda: len(done) >= 2 or len(log.lines) > 400

    def watch(n):
        done.append(n)
    real_remove = q.remove
    q.remove = lambda *a: (real_remove(*a), [watch(x) for x in a])[0]
    mq.run_loop(proc, idle=0, stop=stop)
    assert sorted(c[1] for c in gh.calls if c[0] == "merge" and c[2]) == [60, 61] and q.numbers() == []
    assert events(log).count("UPDATE") + events(log).count("BEHIND-AT-MERGE") >= 1, "the second merge saw main moved by the first"
    assert all(LOG_LINE.match(l) for l in log.lines), "threads do not interleave log lines"
    assert gh.merged_behind == [0, 0], f"a pull request was merged while behind main: {gh.merged_behind}"
    first, second = [c for c in gh.calls if c[0] == "merge" and c[2]]
    assert second[3] != f"{second[1]:040x}", "the second pull request was updated onto the new main before its merge"


def _runner(mq, d):
    # a step that fails is logged and retried with a growing pause, the head stays queued; the third failure in a row is a HOLD and the pull request leaves the queue
    gh = FakeGh(); gh.add(70)
    proc, q, log, slept = _proc(mq, d, "r1", gh, [70, 71])
    gh.add(71)
    boom = {"n": 0}
    real = proc.process

    def flaky(n):
        if n == 70:
            boom["n"] += 1
            if boom["n"] < 3:
                raise SystemExit("gh pr view failed (1)") if boom["n"] == 1 else RuntimeError("socket")
        return real(n)
    proc.process = flaky
    stops = iter([False] * 8 + [True] * 100)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert events(log).count("ERROR") == 2 and [s for s in slept if s in (60, 120)] == [60, 120], (events(log), slept)
    assert q.numbers() == [] and "MERGED" in events(log)
    assert any(l.split()[1] == "DEQUEUE" and l.split()[-1] == "MERGED" for l in log.lines), "DEQUEUE carries the outcome"

    gh = FakeGh(); gh.add(72); gh.add(73)
    proc, q, log, slept = _proc(mq, d, "r2", gh, [72, 73])
    real = proc.process

    def always(n):
        if n == 72:
            raise RuntimeError("always")
        return real(n)
    proc.process = always
    stops = iter([False] * 8 + [True] * 100)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert events(log).count("ERROR") == 3 and "HOLD" in events(log) and q.numbers() == [], (events(log), q.numbers())
    assert "73" in " ".join(l for l in log.lines if " MERGED " in l), "the next pull request is not stuck behind the failing one"


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
    os.makedirs(wt)   # a leftover plain directory (an earlier run was killed): the update must still work
    open(os.path.join(wt, "junk"), "w").write("x")
    assert mq.update_over_ssh(work, "pr-clean", wt) == "ok"
    g(work, "fetch", "-q", "origin")
    assert g(work, "merge-base", "--is-ancestor", "origin/main", "origin/pr-clean") == "" and not os.path.exists(wt) and "mq-wt" not in g(work, "worktree", "list")
    before = g(work, "rev-parse", "origin/pr-conflict")
    assert mq.update_over_ssh(work, "pr-conflict", wt) == "conflict"
    g(work, "fetch", "-q", "origin")
    assert g(work, "rev-parse", "origin/pr-conflict") == before and not os.path.exists(wt) and "mq-wt" not in g(work, "worktree", "list")
    assert mq.update_over_ssh(work, "no-such-branch", wt) == "error"
    lonely = os.path.join(d, "lonely")
    g(d, "init", "-q", "-b", "main", lonely)
    assert mq.update_over_ssh(lonely, "x", wt) == "error", "no origin to fetch from: an error, not a merge of stale refs"
    other = os.path.join(d, "not-ours")
    os.makedirs(other)
    open(os.path.join(other, "keep"), "w").write("x")
    assert mq.update_over_ssh(work, "pr-clean", other) == "error" and os.path.exists(os.path.join(other, "keep")), "a directory that is not the queue's scratch directory is never deleted"


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
    real_replace = os.replace
    try:
        os.replace = lambda *a: (_ for _ in ()).throw(OSError("disk full"))
        try:
            q.enqueue(555)
            raise AssertionError("a failed write must raise")
        except OSError:
            pass
    finally:
        os.replace = real_replace
    assert not [f for f in os.listdir(os.path.dirname(qpath)) if f.startswith(".queue-")], "a failed write leaves no temporary file"
    assert 555 not in q.numbers()
    q.remove(*range(1, 31))
    q.enqueue(1, 2, 3, 4)
    assert q.enqueue(5, 6) == [5, 6] and q.enqueue(5) == [] and q.numbers().count(5) == 1
    q.before(4, 1)
    assert q.numbers()[:2] == [4, 1] and q.numbers().count(4) == 1
    q.before(99, 7777)
    assert q.numbers()[-1] == 99
    open(qpath, "a").write("# note\n")
    q.remove(99); q.enqueue(98)
    assert "# note" in open(qpath).read() and q.numbers()[-1] == 98


def _train(mq):
    r = subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "mq.py"), "train", "--self-test"], capture_output=True, text=True)
    assert r.returncode == 0 and "merge_train self-test: ok" in r.stdout, (r.stdout, r.stderr[-300:])


def _signals(mq, d):
    # gh cannot say how main's runs ended: both lanes wait (nothing is merged on a main nobody can see), no pause file, one log line; when gh answers again they go on
    gh = FakeGh(); gh.add(80)
    proc, q, log, _ = _proc(mq, d, "s1", gh, [80])
    gh.main_runs["push"] = mq.SignalError("rate limited")
    assert proc.pause.check() is True and not proc.pause.is_paused() and proc.pause.check() is True
    assert sum(" WAIT-SIGNAL " in l for l in log.lines) == 1, "logged once, not at every poll"
    gh.main_runs["push"] = None
    assert proc.pause.check() is False

    # resume names exactly the red runs; a run that turned red after the coordinator looked is not acknowledged blind
    gh = FakeGh(); gh.add(81)
    gh.main_runs["push"] = {"id": 500, "status": "completed", "conclusion": "failure", "sha": "a"}
    proc, q, log, _ = _proc(mq, d, "s2", gh, [81])
    assert proc.pause.check() is True
    gh.main_runs["schedule"] = {"id": 501, "status": "completed", "conclusion": "failure", "sha": "b"}
    try:
        proc.pause.resume(["500"])
        raise AssertionError("must refuse")
    except SystemExit as e:
        assert "501" in str(e) and proc.pause.is_paused()
    try:
        proc.pause.resume([])
        raise AssertionError("must refuse")
    except SystemExit:
        pass
    assert sorted(r for _, r in proc.pause.resume(["500", "501"])) == ["500", "501"] and not proc.pause.is_paused() and proc.pause.check() is False

    # one runner per queue
    directory = os.path.join(d, "s3")
    os.makedirs(directory)
    with mq.runner_lock(directory):
        try:
            with mq.runner_lock(directory):
                raise AssertionError("a second runner must be refused")
        except SystemExit as e:
            assert "another" in str(e)
    with mq.runner_lock(directory):
        pass

    # a lane survives whatever one of its steps raises
    gh = FakeGh(); gh.add(82)
    proc, q, log, slept = _proc(mq, d, "s4", gh, [82])
    real = proc.pause.check
    boom = {"n": 0}

    def check():
        boom["n"] += 1
        if boom["n"] == 1:
            raise OSError("disk full")
        return real()
    proc.pause.check = check
    stops = iter([False] * 6 + [True] * 100)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert "ERROR" in events(log) and "disk full" in " ".join(log.lines) and q.numbers() == [] and "MERGED" in events(log), (events(log), q.numbers())

    # the docs lane does not merge while a code pull request is in its final CI; it goes on when that one is done
    gh = FakeGh(); gh.add(83); gh.add(84)
    lanes = {83: "code", 84: "docs"}
    proc, q, log, slept = _proc(mq, d, "s5", gh, [83, 84], lane=lambda g, n: lanes[n])
    proc.code_testing.add(83)

    def release(seconds):
        if seconds == 30 and any(" WAIT-CODE-LANE " in l for l in log.lines):
            proc.code_testing.discard(83)
    proc.sleep = lambda sec: (slept.append(sec), release(sec))
    assert proc.process(84) == "MERGED" and "WAIT-CODE-LANE" in events(log) and events(log).index("WAIT-CODE-LANE") < events(log).index("MERGED")
    gh = FakeGh(); gh.add(85)
    proc, q, log, slept = _proc(mq, d, "s6", gh, [85], lane=lambda g, n: "code")
    proc.code_testing.add(999)
    assert proc.process(85) == "MERGED" and "WAIT-CODE-LANE" not in events(log), "the code lane never waits for the flag"

    # no network failure of the merge decision: compare fails (None) -> HOLD; the pull request closed meanwhile -> HOLD
    gh = FakeGh(); gh.add(86)
    proc, q, log, _ = _proc(mq, d, "s7", gh, [86])
    state = {"n": 0}
    real_behind = gh.behind_by

    def behind(n, head):
        state["n"] += 1
        return None if state["n"] >= 2 else real_behind(n, head)
    gh.behind_by = behind
    assert proc.process(86) == "HOLD" and "whether the head contains main" in log.lines[-1] and not any(c[0] == "merge" for c in gh.calls)
    gh = FakeGh(); gh.add(87)
    proc, q, log, _ = _proc(mq, d, "s8", gh, [87])
    real_view = gh.view
    views = {"n": 0}

    def view(n):
        views["n"] += 1
        v = real_view(n)
        if views["n"] >= 2:   # the first loop view is open, the one under the lock finds it closed
            v["state"] = "CLOSED"
        return v
    gh.view = view
    assert proc.process(87) == "HOLD" and "changed state" in log.lines[-1] and not any(c[0] == "merge" for c in gh.calls)

    # the light run that the label's own run cancelled is no verdict when the full-named twin is listed; a failing full check next to it still is
    both = green() + [dict(c, name=c["name"] + " (light)", bucket="cancel") for c in green()[:2]]
    gh = FakeGh(); gh.add(88, rounds=[dict(pending=0, checks=both)])
    proc, q, log, _ = _proc(mq, d, "s9", gh, [88])
    assert proc.process(88) == "MERGED" and "RERUN-FAILED" not in events(log)
    gh = FakeGh(); gh.add(89, rounds=[dict(pending=0, checks=[dict(c, bucket="fail") if c["name"].startswith("backend") else c for c in both]), dict(pending=0, checks=both)])
    proc, q, log, _ = _proc(mq, d, "s10", gh, [89])
    assert proc.process(89) == "MERGED" and events(log).count("RERUN-FAILED") == 1

    # a red check with main having moved on (strict is off, so no BEHIND state): update and run again, no re-run on the old head
    gh = FakeGh(); gh.add(90, rounds=[dict(pending=0, checks=red(5)), dict(pending=0, checks=green(6))])
    proc, q, log, _ = _proc(mq, d, "s11", gh, [90])
    real_update, real_checks = gh.update_branch, gh.checks

    def update(n):
        gh.prs[n]["round"], gh.prs[n]["pend"] = 1, None   # the update starts a new CI run
        return real_update(n)

    def checks(n):
        out = real_checks(n)
        if gh.prs[n]["round"] == 0:
            gh.prs[n]["behind"] = 3   # main moves while the red run is looked at
        return out
    gh.update_branch, gh.checks = update, checks
    assert proc.process(90) == "MERGED" and "BEHIND-AFTER-FAIL" in events(log) and ("rerun", "5") not in gh.calls, events(log)

    # right after the label only the light run's checks are listed: the queue waits for the full-named backend check instead of judging the light ones
    gh = FakeGh(); gh.add(93)
    proc, q, log, slept = _proc(mq, d, "s11b", gh, [93])
    real_checks = gh.checks
    polls = {"n": 0}

    def late(n):
        polls["n"] += 1
        return green(light=True) if polls["n"] <= 3 else real_checks(n)
    gh.checks = late
    assert proc.process(93) == "MERGED" and slept.count(15) >= 3 and "REQUIRED-NOT-GREEN" not in events(log), (slept, events(log))

    # two ssh updates at once never share the scratch worktree
    inside = {"n": 0, "max": 0}

    def updater(repo, branch, wt):
        inside["n"] += 1
        inside["max"] = max(inside["max"], inside["n"])
        time.sleep(0.05)
        inside["n"] -= 1
        return "ok"
    gh = FakeGh(); gh.add(91, merge="BEHIND", update_ok=False); gh.add(92, merge="BEHIND", update_ok=False)
    proc, q, log, _ = _proc(mq, d, "s12", gh, [91, 92], updater=updater)
    threads = [threading.Thread(target=proc.update, args=(n, f"b{n}", 0)) for n in (91, 92)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    assert inside["max"] == 1, "the scratch worktree of the ssh update is used by one lane at a time"


def _fake_gh_binary(directory, rules):
    """A `gh` on PATH that logs its arguments and answers by prefix: rules = [(argv prefix after gh, stdout, stderr, exit code)]."""
    import json
    os.makedirs(directory, exist_ok=True)
    path = os.path.join(directory, "gh")
    with open(path, "w") as f:
        f.write("#!/usr/bin/env python3\nimport json, os, sys\nargv = sys.argv[1:]\nopen(os.environ['FAKE_GH_LOG'], 'a').write(json.dumps(argv) + '\\n')\n"
                f"rules = json.loads({json.dumps(json.dumps(rules))})\n"
                "for prefix, out, err, rc in rules:\n    if argv[:len(prefix)] == prefix:\n        sys.stdout.write(out); sys.stderr.write(err); sys.exit(rc)\nsys.exit(97)\n")
    os.chmod(path, 0o755)
    return path


def _gh_commands(mq, d):
    """The real Gh class against a fake `gh` binary: the arguments it builds and how it reads the answers."""
    import json
    old = {k: os.environ.get(k) for k in ("PATH", "FAKE_GH_LOG")}
    try:
        directory = os.path.join(d, "fakegh")
        log = os.path.join(d, "fakegh.log")
        rows = [{"databaseId": 7, "status": "in_progress", "conclusion": "", "headSha": "s7", "headBranch": "main"},
                {"databaseId": 6, "status": "completed", "conclusion": "cancelled", "headSha": "s6", "headBranch": "main"},
                {"databaseId": 5, "status": "completed", "conclusion": "failure", "headSha": "s5", "headBranch": "main"},
                {"databaseId": 4, "status": "completed", "conclusion": "success", "headSha": "s4", "headBranch": "main"}]
        rules = [(["pr", "view"], json.dumps({"state": "OPEN", "mergeStateStatus": "CLEAN", "labels": [{"name": "queued"}], "headRefName": "br", "headRefOid": "abc123"}), "", 0),
                 (["api", "repos/{owner}/{repo}/compare/main...abc123"], "3\n", "", 0),
                 (["api", "--paginate", "repos/{owner}/{repo}/pulls/9/files"], "docs/a.md\ndocs/old.md\n", "", 0),
                 (["pr", "checks", "1"], json.dumps([{"name": "x", "bucket": "pass", "link": ""}]), "", 0),
                 (["pr", "checks", "2"], "", "no checks reported on the 'br' branch\n", 0),
                 (["pr", "checks", "3"], "", "network down\n", 1),
                 (["pr", "merge", "4"], "", "Pull request is not mergeable\n", 1),
                 (["pr", "merge", "5"], "merged\n", "", 0),
                 (["run", "list", "--workflow", "ci", "--event", "push"], json.dumps(rows), "", 0),
                 (["run", "list", "--workflow", "ci", "--event", "schedule"], "not json", "boom", 1)]
        _fake_gh_binary(directory, rules)
        os.environ["PATH"] = directory + os.pathsep + os.environ["PATH"]
        os.environ["FAKE_GH_LOG"] = log
        gh = mq.Gh()
        assert gh.view(1) == {"state": "OPEN", "merge": "CLEAN", "labels": ["queued"], "branch": "br", "head": "abc123"}
        assert gh.behind_by(1, "abc123") == 3
        assert gh.files(9) == ["docs/a.md", "docs/old.md"]
        assert gh.checks(1) == [{"name": "x", "bucket": "pass", "link": ""}] and gh.checks(2) == [] and gh.checks(3) is None, "no check yet is an empty list, a failed call is None"
        assert gh.merge(4, "h4") == (False, "Pull request is not mergeable") and gh.merge(5, "h5") == (True, "merged")
        run = gh.main_run("push")
        assert run and run["id"] == 5 and run["conclusion"] == "failure", "the newest ended, not cancelled run decides; a running or cancelled newer one does not hide it"
        try:
            gh.main_run("schedule")
            raise AssertionError("gh failing must raise SignalError")
        except mq.SignalError:
            pass
        calls = [json.loads(l) for l in open(log).read().splitlines()]
        assert ["pr", "merge", "5", "--merge", "--match-head-commit", "h5"] in calls, "the merge is pinned to the head"
        assert ["run", "list", "--workflow", "ci", "--event", "push", "--limit", "20", "--json", "databaseId,status,conclusion,headSha,headBranch", "--branch", "main"] in calls
        assert ["pr", "checks", "1", "--json", "name,bucket,link"] in calls
    finally:
        for k, v in old.items():
            os.environ.pop(k, None) if v is None else os.environ.__setitem__(k, v)
