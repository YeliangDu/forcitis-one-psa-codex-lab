"""Self-test of mq.py (fake gh, real git for the ssh update, real processes and threads for the queue lock and the lanes). Not run on its own: `mq.py --self-test`."""
import contextlib
import importlib.util
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
        self.prs, self.calls, self.runs, self.main_runs, self.lock, self.slow, self.step_failures = {}, [], {}, {"push": None, "schedule": None}, threading.RLock(), False, {}
        self.docker_down = 0

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

    def behind_files(self, head):
        for n, p in self.prs.items():
            if p["head"] == head:
                return p.get("behind_files")
        return None

    def files(self, n):
        return self.prs[n].get("files", [])

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

    def ci_started(self, branch, head):
        for p in self.prs.values():
            if p["branch"] == branch:
                if p.get("ci_late", 0) > 0:
                    p["ci_late"] -= 1
                    return False
        return True

    def cancel_run(self, run):
        self.calls.append(("cancel", run))
        for k in self.runs:
            if run in self.runs[k]:
                self.runs[k].remove(run)
        return True

    def docker_ok(self):
        if self.docker_down > 0:
            self.docker_down -= 1
            return False
        return True

    def failed_steps(self, run):
        return self.step_failures.get(str(run), [])

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
            _ci_step_name(mq)
            _scenarios(mq, d)
            _merge_guards(mq, d)
            _pause(mq, d)
            _signals(mq, d)
            _ff_main(mq, d)
            _remove_busy(mq, d)
            _ignored_conflicts(mq, d)
            _docs_tiers(mq, d)
            _gh_commands(mq, d)
            _lanes(mq, d)
            _urgent(mq, d)
            _dependencies(mq, d)
            _runner(mq, d)
            _ssh_update(mq, d)
            _queue_lock(mq, d)
            _train(mq)
            _gapscan(mq, d)
            _head_changed(mq, d)
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
    kw.setdefault("docs_only_fn", lambda files: bool(files) and all(f.startswith("docs/") and not f.startswith("docs/guarded/") for f in files))
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


def _ci_step_name(mq):
    """The step name the queue looks for is the one ci.yml has (a renamed step would silently turn the no-rerun rule off)."""
    ci = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".github", "workflows", "ci.yml")
    assert f"- name: {mq.REPEAT_STEP}" in open(ci, encoding="utf-8").read(), f"ci.yml has no step named {mq.REPEAT_STEP!r}"


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
    proc, q, log, _ = _proc(mq, d, "c", gh, [6], updater=lambda repo, branch, wt: "conflict")
    assert proc.process(6) == "CONFLICT" and events(log) == ["START", "SSH-UPDATE-CONFLICT", "CONFLICT"] and not any(c[0] == "merge" for c in gh.calls), events(log)

    # a conflict only in products of build_all.py: the update settles it (AUTO-RESOLVE) and the pull request goes on to its CI and merge; gh update-branch is not asked about a conflicted branch
    def regenerating_for(gh, n):
        def update(repo, branch, wt):
            gh.prs[n]["merge"] = "CLEAN"
            return "regenerated"
        return update
    gh = FakeGh(); gh.add(60, merge="DIRTY")

    def regenerating(repo, branch, wt):
        gh.prs[60]["merge"] = "CLEAN"
        return "regenerated"
    proc, q, log, _ = _proc(mq, d, "c-auto", gh, [60], updater=regenerating)
    assert proc.process(60) == "MERGED" and "AUTO-RESOLVE" in events(log) and "CONFLICT" not in events(log) and ("update_branch", 60) not in gh.calls, events(log)
    gh = FakeGh(); gh.add(62, merge="DIRTY")
    proc, q, log, _ = _proc(mq, d, "c-settle", gh, [62], updater=regenerating_for(gh, 62))
    settled, why = proc.settle_dirty(62, gh.view(62), None)
    assert settled["merge"] == "CLEAN" and why is None and "AUTO-RESOLVE" in events(log), "settled"
    gh = FakeGh(); gh.add(63, merge="DIRTY")
    proc, q, log, _ = _proc(mq, d, "c-settle2", gh, [63], updater=lambda repo, branch, wt: "conflict")
    assert proc.settle_dirty(63, gh.view(63), None) == (None, "conflict")
    gh = FakeGh(); gh.add(64, merge="DIRTY")
    proc, q, log, _ = _proc(mq, d, "c-settle3", gh, [64], updater=lambda repo, branch, wt: "error")
    assert proc.settle_dirty(64, gh.view(64), None) == (None, "error") and proc.process(64) == "HOLD" and "CONFLICT" not in events(log), "an update that failed is a HOLD, not a conflict"
    # GitHub sees the push late: DIRTY for the first two looks, then clean
    gh = FakeGh(); gh.add(65, merge="DIRTY")
    looks = {"n": 0}
    real_view = gh.view

    def late_view(n):
        v = real_view(n)
        looks["n"] += 1
        if looks["n"] >= 4:
            gh.prs[n]["merge"] = "CLEAN"
        return v
    gh.view = late_view
    proc, q, log, slept = _proc(mq, d, "c-late", gh, [65], updater=lambda repo, branch, wt: "regenerated")
    assert proc.process(65) == "MERGED" and "CONFLICT" not in events(log) and slept.count(30) >= 2, (events(log), slept)
    gh = FakeGh(); gh.add(61, merge="DIRTY")
    proc, q, log, _ = _proc(mq, d, "c-still", gh, [61], updater=lambda repo, branch, wt: "regenerated")
    assert proc.process(61) == "CONFLICT" and "AUTO-RESOLVE" in events(log), "regenerated but GitHub still sees a conflict: back to the author"

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

    # the repeat step of ci.yml is red: a new test is flaky, so no rerun, HOLD at once; another red step is re-run as before
    gh = FakeGh(); gh.add(110, rounds=[dict(pending=0, checks=red(31)), dict(pending=0, checks=green(32))])
    gh.step_failures = {"31": ["new or changed test classes, repeated"]}
    proc, q, log, _ = _proc(mq, d, "rp1", gh, [110])
    assert proc.process(110) == "HOLD" and not any(c[0] == "rerun" for c in gh.calls) and "repeat runs" in log.lines[-1] and "run=31" in log.lines[-1] and not any(c[0] == "merge" for c in gh.calls)
    # the environment failed (container runtime down), not the pull request: wait for it, run again, no attempt used up; it never recovers: HOLD naming the environment
    gh = FakeGh(); gh.add(113, rounds=[dict(pending=0, checks=red(61)), dict(pending=0, checks=red(62)), dict(pending=0, checks=green(63))]); gh.docker_down = 3
    gh.step_failures = {"61": ["test images are present"]}
    proc, q, log, slept = _proc(mq, d, "env1", gh, [113])
    assert proc.process(113) == "MERGED" and "ENV" in events(log) and slept.count(60) == 2 and ("rerun", "61") in gh.calls, (events(log), slept)
    gh = FakeGh(); gh.add(114, rounds=[dict(pending=0, checks=red(71))]); gh.docker_down = 10 ** 6
    gh.step_failures = {"71": ["docker is running"]}
    proc, q, log, _ = _proc(mq, d, "env2", gh, [114])
    assert proc.process(114) == "HOLD" and "environment" in log.lines[-1] and not any(c[0] == "rerun" for c in gh.calls), log.lines[-1]
    gh = FakeGh(); gh.add(115, rounds=[dict(pending=0, checks=red(81))] * 5)
    gh.step_failures = {"81": ["test images are present"]}
    flips = iter([False, True] * 20)   # down when the verdict is made, up again when waited for
    gh.docker_ok = lambda: next(flips)
    proc, q, log, _ = _proc(mq, d, "env3", gh, [115])
    assert proc.process(115) == "HOLD" and events(log).count("ENV") == 4 and "environment" in log.lines[-1], "an environment that keeps failing is not rerun for ever"
    # not an environment failure: docker answers (a wrong test-images.txt), or another step failed too, or one of two red runs failed in a real step
    gh = FakeGh(); gh.add(116, rounds=[dict(pending=0, checks=red(91)), dict(pending=0, checks=green(92))])
    gh.step_failures = {"91": ["test images are present"]}
    proc, q, log, _ = _proc(mq, d, "env4", gh, [116])
    assert proc.process(116) == "MERGED" and "ENV" not in events(log) and "RERUN-FAILED" in events(log), "docker is fine: the pull request's own failure, rerun once as any other"
    gh = FakeGh(); gh.add(117, rounds=[dict(pending=0, checks=red(93)), dict(pending=0, checks=green(94))]); gh.docker_down = 10 ** 6
    gh.step_failures = {"93": ["docker is running", "surefire and failsafe, side by side"]}
    proc, q, log, _ = _proc(mq, d, "env5", gh, [117])
    assert proc.process(117) == "MERGED" and "ENV" not in events(log), "a real failure next to the environment step is not swallowed"
    gh = FakeGh(); gh.add(118, rounds=[dict(pending=0, checks=red(95) + red(96)), dict(pending=0, checks=green(97))]); gh.docker_down = 10 ** 6
    gh.step_failures = {"95": ["docker is running"], "96": ["surefire and failsafe, side by side"]}
    proc, q, log, _ = _proc(mq, d, "env6", gh, [118])
    assert "ENV" not in events(log) and proc.process(118) == "MERGED", "one red run with a real failure: no environment verdict for the batch"
    gh = FakeGh(); gh.add(112, rounds=[dict(pending=0, checks=red(51)), dict(pending=0, checks=green(52))])
    gh.failed_steps = lambda run: None
    proc, q, log, _ = _proc(mq, d, "rp3", gh, [112])
    assert proc.process(112) == "MERGED" and ("rerun", "51") in gh.calls and "FAILED-STEPS-UNKNOWN" in events(log), "unknown steps: rerun as before, and say so"
    gh = FakeGh(); gh.add(111, rounds=[dict(pending=0, checks=red(41)), dict(pending=0, checks=green(42))])
    gh.step_failures = {"41": ["surefire and failsafe, side by side"]}
    proc, q, log, _ = _proc(mq, d, "rp2", gh, [111])
    assert proc.process(111) == "MERGED" and ("rerun", "41") in gh.calls, "any other red step is re-run once"

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
    # after the update the queue waits for the CI of the new head to start; a main-moved verdict before that is not counted
    gh = FakeGh(); gh.add(134, merge="BEHIND", behind=2); gh.prs[134]["ci_late"] = 3
    proc, q, log, slept = _proc(mq, d, "nc1", gh, [134])
    assert proc.process(134) == "MERGED" and slept.count(15) >= 3 and "NEW-CI-NOT-SEEN" not in events(log), (events(log), slept)
    gh = FakeGh(); gh.add(135, merge="BEHIND", behind=2); gh.prs[135]["ci_late"] = 10 ** 6
    proc, q, log, _ = _proc(mq, d, "nc2", gh, [135])
    behind_values = iter([2] + [1] * 80)
    gh.behind_by = lambda n, head: next(behind_values)
    assert proc.process(135) == "HOLD" and "NEW-CI-NOT-SEEN" in events(log) and "MOVED-NOT-COUNTED" in events(log) and "main moved" not in log.lines[-1] and "never started" in log.lines[-1], (events(log), log.lines[-1])

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


def _urgent(mq, d):
    # an urgent pull request is picked before the ones queued earlier, and the queue does not cancel its CI runs
    gh = FakeGh()
    for n in (80, 81, 82):
        gh.add(n)
    gh.prs[82]["labels"].add(mq.URGENT_LABEL)
    proc, q, log, _ = _proc(mq, d, "u1", gh, [80, 81, 82])
    assert proc.pick("code") == 82 and proc.pick("code") == 80 and proc.pick("code") == 81, "urgent first, the rest in queue order"
    gh = FakeGh()
    for n in (83, 84):
        gh.add(n)
        gh.runs[f"b{n}"] = [n * 10]
    gh.prs[84]["labels"].add(mq.URGENT_LABEL)
    proc, q, log, _ = _proc(mq, d, "u2", gh, [83, 84, 85])
    gh.add(85); gh.runs["b85"] = [850]
    proc.cancel_others(83, None)
    assert ("cancel", 840) not in gh.calls and ("cancel", 850) in gh.calls, "only the pull request without the label loses its run"


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


def _dependencies(mq, d):
    # the queue file: "<n> after=<m>,<m>", kept through before(), read by numbers(), refused when it loops
    gh = FakeGh()
    for n in (70, 71, 72, 73):
        gh.add(n)
    proc, q, log, _ = _proc(mq, d, "dep1", gh, [])
    assert q.enqueue(71, after=[70]) == [71] and q.enqueue(70, 72) == [70, 72] and q.numbers() == [71, 70, 72]
    assert open(q.path).read() == "71 after=70\n70\n72\n" and q.deps(71) == {70} and q.deps(70) == set() and q.deps(99) == set()
    assert q.enqueue(71, after=[72]) == [] and q.deps(71) == {70, 72} and open(q.path).read().splitlines()[0] == "71 after=70,72", "an already queued entry gains the dependency"
    q.before(71, 72)
    assert open(q.path).read().splitlines() == ["70", "71 after=70,72", "72"], "before() keeps the dependency"
    for bad in (lambda: q.enqueue(70, after=[70]), lambda: q.enqueue(70, after=[71]), lambda: q.enqueue(72, after=[71])):
        try:
            bad()
        except ValueError:
            pass
        else:
            raise AssertionError("a self-dependency or a cycle was accepted")
    assert q.deps(70) == set() and q.deps(72) == set(), "a refused enqueue changed nothing"
    q.remove(70)
    assert q.numbers() == [71, 72] and q.deps(71) == {70, 72}, "a dependency stays recorded when the pull request it names leaves the queue"
    with open(q.path, "a") as f:
        f.write("keep me\n")
    q.enqueue(73, after=[70])
    assert "keep me" in open(q.path).read()

    # pick: a waiting pull request is skipped (once in the log per change), the next one is not held up; merged dependency releases it
    lanes = {70: "docs", 71: "code", 72: "code", 73: "code"}
    gh = FakeGh()
    for n in lanes:
        gh.add(n)
    proc, q, log, _ = _proc(mq, d, "dep2", gh, [], lane=lambda g, n: lanes[n])
    q.enqueue(71, after=[70]); q.enqueue(72, 70)
    assert proc.pick("code") == 72, "71 waits for 70, 72 does not"
    assert proc.pick("code") is None and proc.pick("code") is None
    waits = [l for l in log.lines if " WAIT-DEP " in l]
    assert len(waits) == 1 and "pr=#71" in waits[0] and "lane=code" in waits[0] and "waits for #70" in waits[0], waits
    gh.prs[70]["state"] = "MERGED"
    assert proc.pick("code") == 71 and not proc.waiting, "released once the dependency is merged"
    assert len([l for l in log.lines if " WAIT-DEP " in l]) == 1

    # gh cannot say what a dependency is: not released, and the log says so
    gh = FakeGh(); gh.add(76); gh.add(77)
    proc, q, log, _ = _proc(mq, d, "dep6", gh, [])
    q.enqueue(77, after=[76])
    real_view = gh.view
    gh.view = lambda n: None if n == 76 else real_view(n)
    assert proc.pick("code") is None and any("gh could not tell the state of #76" in l and "(not in the queue)" in l for l in log.lines), log.lines
    # a dependency that never merges stays loud: logged again after wait_repeat seconds, not on every round
    gh.view = real_view
    n_before = len(log.lines)
    assert proc.pick("code") is None and len(log.lines) == n_before, "same dependency, no repeat yet"
    proc.wait_repeat = 0
    assert proc.pick("code") is None and len(log.lines) == n_before + 1 and " WAIT-DEP " in log.lines[-1], "repeated once the interval has passed"

    # the command line: values are checked, one call queues all or nothing, unreadable lines are named
    qpath, lpath = os.path.join(d, "cli", "merge.queue"), os.path.join(d, "cli", "merge.log")
    script = os.path.abspath(mq.__file__)

    def cli(*args):
        return subprocess.run([sys.executable, script, "--queue", qpath, "--log", lpath, *args], capture_output=True, text=True)
    assert cli("enqueue", "449", "--after", "448").returncode == 0 and open(qpath).read() == "449 after=448\n"
    for bad in (["enqueue", "500", "--after=-5"], ["enqueue", "500", "--after", "0"], ["enqueue", "500", "--after", "x"]):
        r = cli(*bad)
        assert r.returncode != 0 and "500" not in open(qpath).read(), (bad, r.stderr)
    r = cli("enqueue", "452", "451", "--after", "451")
    assert r.returncode != 0 and "nothing queued" in r.stderr and open(qpath).read() == "449 after=448\n", "a refused call queues none of its pull requests"
    r = cli("enqueue", "449", "--after", "447")
    assert r.returncode == 0 and "already queued" in r.stderr and open(qpath).read() == "449 after=447,448\n"
    with open(qpath, "a") as f:
        f.write("600 after=#448\n")
    r = cli("list")
    assert r.stdout.split("\n")[0] == "449 after=447,448" and "600" in r.stderr and "not understood" in r.stderr, (r.stdout, r.stderr)

    # a dependency closed without merge: HOLD, taken out of the queue
    gh = FakeGh()
    for n in (74, 75):
        gh.add(n)
    gh.prs[74]["state"] = "CLOSED"
    proc, q, log, _ = _proc(mq, d, "dep3", gh, [])
    q.enqueue(75, after=[74])
    assert proc.pick("code") is None and q.numbers() == [], "taken out"
    assert any(" HOLD " in l and "pr=#75" in l and "dependency #74 was closed without merge" in l for l in log.lines) and events(log) == ["HOLD", "DEQUEUE"], log.lines

    # across lanes with the real runner: 81 (code lane) is queued first but waits for 80 (docs lane): 80 merges first
    lanes = {80: "docs", 81: "code"}
    gh = FakeGh(); gh.add(80); gh.add(81)
    proc, q, log, _ = _proc(mq, d, "dep4", gh, [], lane=lambda g, n: lanes[n])
    q.enqueue(81, after=[80]); q.enqueue(80)
    mq.run_loop(proc, idle=0, stop=lambda: q.numbers() == [] or len(log.lines) > 600)
    assert [c[1] for c in gh.calls if c[0] == "merge" and c[2]] == [80, 81] and q.numbers() == [], gh.calls
    starts = [l for l in log.lines if " START " in l]
    assert [("pr=#80" in l and "lane=docs" in l) or ("pr=#81" in l and "lane=code" in l) for l in starts] == [True, True] and "pr=#81" in starts[1], starts
    assert all("lane=" in l for l in log.lines if "pr=#80" in l or "pr=#81" in l), "events of a taken pull request carry its lane"
    assert all(LOG_LINE.match(l) for l in log.lines)

    # `once` (process without pick) learns the lane from the lane function
    gh = FakeGh(); gh.add(90)
    proc, q, log, _ = _proc(mq, d, "dep5", gh, [], lane=lambda g, n: "docs")
    proc.process(90)
    assert " START " in log.lines[0] and "lane=docs" in log.lines[0] and all("lane=docs" in l for l in log.lines), log.lines
    # a line of the log that names no pull request has no lane
    log.event("ERROR", None, None, None, "x")
    assert "lane=" not in log.lines[-1]


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
    _regenerated(mq, d)
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

    # the docs lane never waits for the code lane: a docs merge leaves a code pull request counted as up to date (see the docs-tier cases below)
    gh = FakeGh(); gh.add(84)
    proc, q, log, slept = _proc(mq, d, "s5", gh, [84], lane=lambda g, n: "docs")
    assert proc.process(84) == "MERGED" and 30 not in slept, (events(log), slept)

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

    # a pull request that changes .github/workflows (or whose file list gh cannot give) is never updated with `gh pr update-branch` (it exits 0 without pushing: the token lacks the workflow scope): ssh straight away; any other still goes through gh
    for name, files, via_ssh in (("plain", ["backend/a.java"], False), ("workflow", ["backend/a.java", ".github/workflows/ci.yml"], True), ("unknown", None, True)):
        gh = FakeGh(); gh.add(110, behind=3)
        gh.prs[110]["files"] = files
        seen = []
        proc, q, log, _ = _proc(mq, d, "wf-" + name, gh, [110], updater=lambda repo, branch, wt, gh=gh, seen=seen: (seen.append(branch), gh.prs[110].update(merge="CLEAN", behind=0), "ok")[2])
        assert proc.process(110) == "MERGED", name
        assert (("update-branch", 110) in gh.calls) == (not via_ssh) and bool(seen) == via_ssh and ("SSH-UPDATE" in events(log)) == via_ssh, (name, gh.calls, events(log))

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

    # behind main by docs-only commits: counted as up to date (no update, no new run, merged as it is); by a middle-tier (guarded) or code file: synced and tested again; a list gh cannot give: synced
    for name, files, synced in (("only", ["docs/a.md", "docs/b/c.md"], False), ("guarded", ["docs/a.md", "docs/guarded/terms.yaml"], True), ("code", ["docs/a.md", "backend/pom.xml"], True), ("unknown", None, True), ("empty", [], True)):
        gh = FakeGh(); gh.add(100, behind=3)
        gh.prs[100]["behind_files"] = files
        proc, q, log, _ = _proc(mq, d, "bd-" + name, gh, [100])
        assert proc.process(100) == "MERGED", name
        assert (("update-branch", 100) in gh.calls) == synced, (name, events(log))
        if not synced:
            assert gh.merged_behind == [3], "merged while behind docs-only commits, on purpose"
    # strict protection still on, main moved by a docs commit while the full CI ran: GitHub says BEHIND although the queue counts the head as up to date. No merge is attempted; sync and run again, no HOLD, no refusal counted
    gh = FakeGh(); gh.add(102)
    gh.prs[102]["behind_files"] = ["docs/a.md"]
    proc, q, log, _ = _proc(mq, d, "bd-strict2", gh, [102])
    real_checks = gh.checks
    polls = {"n": 0}

    def strict_main_moves(n):
        polls["n"] += 1
        if polls["n"] == 2:   # the checks have just ended green; main moves by a docs commit
            gh.prs[102]["merge"], gh.prs[102]["behind"] = "BEHIND", 1
        return real_checks(n)
    gh.checks = strict_main_moves
    assert proc.process(102) == "MERGED", events(log)
    assert events(log).count("BEHIND-AT-MERGE") == 1 and "UPDATE" in events(log) and "RETRY-MERGE" not in events(log) and "HOLD" not in events(log), events(log)
    kinds = [c[0] for c in gh.calls]
    assert kinds.count("merge") == 1 and kinds.index("update-branch") < kinds.index("merge"), "gh merge was called once, after the sync"

    # the same, but main moves between our look and the merge itself: gh refuses (BEHIND), and that is a sync again, not a refusal; it never reaches the five refusals
    gh = FakeGh(); gh.add(103, merge_results=((False, "Head branch is not up to date with the base branch"), (False, "Head branch is not up to date with the base branch"), (True, "")))
    gh.prs[103]["behind_files"] = ["docs/a.md"]
    proc, q, log, _ = _proc(mq, d, "bd-strict3", gh, [103])
    real_merge = gh.merge

    def merge_then_behind(n, head):
        out = real_merge(n, head)
        if not out[0]:
            gh.prs[n]["merge"], gh.prs[n]["behind"] = "BEHIND", 1   # GitHub's answer to a refused merge: the branch is behind
        return out
    gh.merge = merge_then_behind
    assert proc.process(103) == "MERGED" and events(log).count("UPDATE") == 2 and events(log).count("RETRY-MERGE") == 2, events(log)
    gh = FakeGh(); gh.add(104, merge_results=((False, "behind"),) * 20)
    gh.prs[104]["behind_files"] = ["docs/a.md"]
    proc, q, log, _ = _proc(mq, d, "bd-strict4", gh, [104])
    real_merge = gh.merge
    gh.merge = lambda n, head: (lambda out: (gh.prs[n].update(merge="BEHIND", behind=1), out)[1])(real_merge(n, head))
    assert proc.process(104) == "HOLD" and "main moved" in log.lines[-1] and "refused" not in log.lines[-1], "main that keeps moving ends in the moved limit, not in a refusal count"

    # the strict protection still being on (GitHub says BEHIND) keeps the sync: GitHub would refuse the merge otherwise
    gh = FakeGh(); gh.add(101, merge="BEHIND", behind=2)
    gh.prs[101]["behind_files"] = ["docs/a.md"]
    proc, q, log, _ = _proc(mq, d, "bd-strict", gh, [101])
    assert proc.process(101) == "MERGED" and ("update-branch", 101) in gh.calls

    # checks not registered yet (an empty list) are waited for, not a HOLD at once; main moved meanwhile, so the pull request is brought up to date again
    gh = FakeGh(); gh.add(97)
    proc, q, log, slept = _proc(mq, d, "s11f", gh, [97])
    real_checks = gh.checks
    polls = {"n": 0}

    def late_checks(n):
        polls["n"] += 1
        if polls["n"] == 23:   # the checks end green; main moves before the merge is decided
            gh.prs[97]["behind"] = 1
        return [] if polls["n"] <= 22 else real_checks(n)   # nothing listed through the registration wait (20 polls) and two more

    gh.checks = late_checks
    assert proc.process(97) == "MERGED" and "BEHIND-AT-MERGE" in events(log), events(log)

    # a runner that cannot even take a pull request out after three failures keeps its lane
    gh = FakeGh(); gh.add(96)
    proc, q, log, slept = _proc(mq, d, "s11e", gh, [96])
    proc.process = lambda n: (_ for _ in ()).throw(RuntimeError("always"))
    real_remove = q.remove
    q.remove = lambda *a: (_ for _ in ()).throw(OSError("read-only")) if not getattr(q, "ok", False) else real_remove(*a)
    stops = iter([False] * 6 + [True] * 100)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert events(log).count("ERROR") >= 3, events(log)

    # a hanging call is a failed call: sh() gives up after its timeout
    r = mq.sh([sys.executable, "-c", "import time; time.sleep(300)"], timeout=1)
    assert r.returncode == 124 and "timed out" in r.stderr   # a timeout that did not work would come back 0 after 300 s (no wall-clock bound: a loaded machine made it flaky)

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
                 (["api", "repos/{owner}/{repo}/compare/abc123...main", "--jq", "[.files[] | .filename, .previous_filename] | map(select(. != null)) | .[]"], "docs/x.md\ndocs/old.md\n", "", 0),
                 (["api", "repos/{owner}/{repo}/compare/abc123...main", "--jq", ".files | length"], "2\n", "", 0),
                 (["api", "repos/{owner}/{repo}/compare/big000...main", "--jq", "[.files[] | .filename, .previous_filename] | map(select(. != null)) | .[]"], "docs/x.md\n", "", 0),
                 (["api", "repos/{owner}/{repo}/compare/big000...main", "--jq", ".files | length"], "300\n", "", 0),
                 (["run", "view", "7", "--json", "jobs", "--jq", '.jobs[].steps[] | select(.conclusion == "failure") | .name'], "step one\nnew or changed test classes, repeated\n", "", 0),
                 (["run", "view", "8", "--json", "jobs", "--jq", '.jobs[].steps[] | select(.conclusion == "failure") | .name'], "", "boom", 1),
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
        assert gh.behind_files("abc123") == ["docs/x.md", "docs/old.md"] and gh.behind_files("big000") is None, "a cut-off list (300 files) is no answer"
        assert gh.checks(1) == [{"name": "x", "bucket": "pass", "link": ""}] and gh.checks(2) == [] and gh.checks(3) is None, "no check yet is an empty list, a failed call is None"
        assert gh.failed_steps(7) == ["step one", "new or changed test classes, repeated"] and gh.failed_steps(8) is None
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


def _git(*args, cwd):
    r = subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", *args], cwd=cwd, capture_output=True, text=True)
    assert r.returncode == 0, (args, r.stderr)
    return r.stdout.strip()


def _ff_main(mq, d):
    # WAIT-SIGNAL is logged for the first unreadable poll whatever the machine's uptime is (monotonic time may be small)
    gh = FakeGh(); gh.add(200)
    proc, q, log, _ = _proc(mq, d, "ffs", gh, [200])
    gh.main_runs["push"] = mq.SignalError("rate limited")
    proc.pause.clock = lambda: 5.0
    assert proc.pause.check() is True
    assert sum(" WAIT-SIGNAL " in l for l in log.lines) == 1, "the first line must not depend on how long the machine has been up"

    # the main checkout after a merge: fast-forwarded when it is clean and on main; left alone, ALERT and both lanes paused otherwise
    origin = os.path.join(d, "ff-origin.git")
    _git("init", "-q", "--bare", "-b", "main", origin, cwd=d)
    seed = os.path.join(d, "ff-seed")
    _git("clone", "-q", origin, seed, cwd=d)
    open(os.path.join(seed, "a"), "w").write("1\n")
    open(os.path.join(seed, "c"), "w").write("c\n")
    _git("add", "a", "c", cwd=seed); _git("commit", "-qm", "one", cwd=seed); _git("push", "-q", "origin", "main", cwd=seed)

    def checkout(name):
        path = os.path.join(d, name)
        _git("clone", "-q", origin, path, cwd=d)
        return path

    def advance(text):
        open(os.path.join(seed, "a"), "w").write(text)
        _git("commit", "-qam", text.strip(), cwd=seed); _git("push", "-q", "origin", "main", cwd=seed)

    def run_ff(name, path):
        gh = FakeGh(); gh.add(201)
        proc, q, log, _ = _proc(mq, d, name, gh, [201], main_checkout=path)
        proc.ff_main(201, 0)
        return proc, log

    main_dir = checkout("ff-ok")
    advance("2\n")
    proc, log = run_ff("ff1", main_dir)
    assert "FF-MAIN" in events(log) and "ALERT" not in events(log) and _git("rev-parse", "HEAD", cwd=main_dir) == _git("rev-parse", "origin/main", cwd=main_dir) and not proc.pause.is_paused(), log.lines

    wrong = checkout("ff-branch")
    _git("checkout", "-q", "-b", "work", cwd=wrong)
    advance("3\n")
    before = _git("rev-parse", "HEAD", cwd=wrong)
    proc, log = run_ff("ff2", wrong)
    assert "ALERT" in events(log) and "FF-MAIN" not in events(log) and proc.pause.is_paused() and "work" in open(proc.pause.paused_file).read() and _git("rev-parse", "HEAD", cwd=wrong) == before, log.lines

    dirty = checkout("ff-dirty")
    advance("4\n")
    open(os.path.join(dirty, "c"), "w").write("mine\n")   # a tracked file the fast-forward would not touch: git alone would not refuse
    proc, log = run_ff("ff3", dirty)
    assert "ALERT" in events(log) and proc.pause.is_paused() and open(os.path.join(dirty, "c")).read() == "mine\n" and _git("rev-parse", "HEAD", cwd=dirty) != _git("rev-parse", "origin/main", cwd=dirty), log.lines

    local = checkout("ff-local")
    open(os.path.join(local, "b"), "w").write("x\n")
    _git("add", "b", cwd=local); _git("commit", "-qm", "local only", cwd=local)   # origin has nothing new: git alone would call the fast-forward a success
    before = _git("rev-parse", "HEAD", cwd=local)
    proc, log = run_ff("ff4", local)
    assert "ALERT" in events(log) and proc.pause.is_paused() and _git("rev-parse", "HEAD", cwd=local) == before, log.lines
    assert proc.pause.check() is True, "both lanes wait"
    proc.pause.resume([])
    assert not proc.pause.is_paused(), "a person's resume lets them go on"

    # a Python file under docs/ is check logic, not plain documentation: a code pull request that lacks it is tested again
    gh = FakeGh(); gh.add(204, behind=1)
    gh.prs[204]["behind_files"] = ["docs/x/check_something.py"]
    proc, q, log, _ = _proc(mq, d, "ffpy", gh, [204], docs_only_fn=lambda files: True)
    assert proc.process(204) == "MERGED" and "UPDATE" in events(log), events(log)

    # the fast-forward runs under its own lock, and a git command that fails on another command's lock is tried again before anybody is called
    lock_seen = []
    gh = FakeGh(); gh.add(205)
    proc, q, log, slept = _proc(mq, d, "fflock", gh, [205], main_checkout=checkout("ff-lockrun"))
    real_locked = proc.queue.locked
    proc.queue.locked = lambda suffix=".lock": (lock_seen.append(suffix), real_locked(suffix))[1]
    real_sh, tries = mq.sh, {"n": 0}

    def flaky(args, **kw):
        if "merge" in args and "--ff-only" in args and tries["n"] < 2:
            tries["n"] += 1
            return subprocess.CompletedProcess(args, 128, "", "fatal: Unable to create '.git/index.lock': File exists.")
        return real_sh(args, **kw)
    mq.sh = flaky
    try:
        proc.ff_main(205, 0)
    finally:
        mq.sh = real_sh
    assert lock_seen == [".ff-lock"] and tries["n"] == 2 and "FF-MAIN" in events(log) and "ALERT" not in events(log) and slept.count(5) == 2, (lock_seen, tries, log.lines)

    # the main checkout is found as the first worktree, from a worktree too; a bare repository has none
    root = checkout("ff-where")
    _git("worktree", "add", "-q", os.path.join(d, "ff-where-wt"), "-b", "other", cwd=root)
    cwd = os.getcwd()
    try:
        os.chdir(os.path.join(d, "ff-where-wt"))
        assert os.path.realpath(mq.main_checkout()) == os.path.realpath(root)
        os.chdir(d)
        os.chdir(origin)
        assert mq.main_checkout() is None
    finally:
        os.chdir(cwd)

    # unreachable origin: only logged, nothing paused
    broken = checkout("ff-fetch")
    _git("remote", "set-url", "origin", os.path.join(d, "nowhere.git"), cwd=broken)
    proc, log = run_ff("ff5", broken)
    assert "ALERT" in events(log) and not proc.pause.is_paused(), log.lines

    # the merge itself calls it, and only after a merge that happened
    gh = FakeGh(); gh.add(202); gh.add(203, merge_results=[(False, "not mergeable")] * 6)
    proc, q, log, _ = _proc(mq, d, "ff6", gh, [202, 203])
    called = []
    proc.ff_main = lambda n, since: called.append(n)
    assert proc.process(202) == "MERGED" and called == [202]
    proc.process(203)
    assert called == [202], "a refused merge does not touch the main checkout"


def _docs_tiers(mq, d):
    # the verdict the queue relies on, from the real relevant_change.py: a doc a test reads is never tier=docs, so a docs merge never makes a code pull request rerun
    repo = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
    check = mq.docs_only(repo)
    spec = importlib.util.spec_from_file_location("relevant_change", os.path.join(repo, "scripts", "ci", "relevant_change.py"))
    rc = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(rc)
    plain = next(f for f in subprocess.run(["git", "-c", "core.quotepath=off", "ls-files", "docs"], cwd=repo, capture_output=True, text=True).stdout.split() if f.endswith(".md") and not rc.is_relevant(f))
    assert check([plain]), "plain documentation is tier=docs"
    # docs that only a container-free test reads (tier=guarded) are found, not named: relevant_change.py takes every script that names a doc for a reader of it
    listed = [f for f in subprocess.run(["git", "-c", "core.quotepath=off", "ls-files", "docs"], cwd=repo, capture_output=True, text=True).stdout.split() if rc.is_guarded_doc(f)]
    index, light = rc.ReaderIndex(repo), rc.light_class_names(repo)
    guarded = [f for f in listed if (rc.plan_for([f], index, light) or {}).get("classes")]
    assert guarded, "no doc of the guarded tier is left in the repository: the guarded case below would silently vanish; pick the sample differently"
    for read in ["docs/modernization/events/asyncapi.yaml", "docs/modernization/glossary/terms.yaml", "docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml", *guarded]:
        assert not check([read]), f"{read} is read by a test: not docs"
        assert not check([plain, read]), "one test-read file among docs makes the whole set non-docs"

    # a code pull request whose CI ends after main gained docs commits merges as it is; after a test-read doc or code, it is brought up to date and run again
    for name, files, reruns in (("docs", [plain], False), ("guarded", guarded[:1], True), ("code", ["docs/modernization/events/asyncapi.yaml"], True), ("mixed", [plain, "backend/pom.xml"], True)):
        gh = FakeGh(); gh.add(210, behind=1)
        gh.prs[210]["behind_files"] = files
        proc, q, log, _ = _proc(mq, d, f"dt-{name}", gh, [210], docs_only_fn=check)
        assert proc.process(210) == "MERGED", log.lines
        assert ("UPDATE" in events(log)) is reruns, (name, events(log))


# the stand-in tree needs mq.py's own path of build_all.py; written in pieces because relevant_change.py takes every script that names a doc path for a reader of it
MOD = "docs" + "/" + "modernization"
BUILD_ALL_SOURCE = """import os, sys
here = os.path.dirname(os.path.abspath(__file__))
if "--check" in sys.argv:
    if os.path.exists(os.path.join(here, "CHECKFAIL")):
        sys.exit("check fails")
    want = open(os.path.join(here, "GEN.md"), encoding="utf-8").read()
    got = "".join(open(os.path.join(here, "inputs", n), encoding="utf-8").read() for n in sorted(os.listdir(os.path.join(here, "inputs"))))
    sys.exit(0 if want == got else "GEN.md is not what the inputs generate")
if os.path.exists(os.path.join(here, "WRITELOG")):
    open(os.path.join(here, "build.log"), "w").write("log")
if os.path.exists(os.path.join(here, "FAIL")):
    sys.exit("generation failed")
text = "".join(open(os.path.join(here, "inputs", n), encoding="utf-8").read() for n in sorted(os.listdir(os.path.join(here, "inputs"))))
open(os.path.join(here, "GEN.md"), "w", encoding="utf-8").write(text)
"""


def _regenerated(mq, d):
    """Real git and a stand-in build_all.py: a merge that conflicts only in what build_all.py generates is finished by regenerating it; a conflict in anything a person wrote is not."""
    def g(cwd, *a):
        r = subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false", *a], cwd=cwd, capture_output=True, text=True)
        assert r.returncode == 0, (a, r.stderr)
        return r.stdout.strip()

    origin, work = os.path.join(d, "r.git"), os.path.join(d, "rw")
    g(d, "init", "-q", "--bare", "-b", "main", origin)
    g(d, "clone", "-q", origin, work)
    os.makedirs(os.path.join(work, f"{MOD}/inputs"))

    def write(path, text):
        with open(os.path.join(work, path), "w", encoding="utf-8") as f:
            f.write(text)

    def commit(msg):
        g(work, "add", "-A"); g(work, "commit", "-q", "-m", msg)

    write(f"{MOD}/build_all.py", BUILD_ALL_SOURCE)
    write(f"{MOD}/inputs/a.txt", "a\n"); write(f"{MOD}/inputs/b.txt", "b\n"); write(f"{MOD}/GEN.md", "a\nb\n")
    commit("base"); g(work, "push", "-q", "origin", "main")

    def branch(name, edits):
        g(work, "checkout", "-q", "-B", name, "main")
        for path, text in edits.items():
            write(path, text)
        commit(name); g(work, "push", "-q", "origin", name)

    branch("pr-gen", {f"{MOD}/inputs/a.txt": "a2\n", f"{MOD}/GEN.md": "a2\nb\n"})
    branch("pr-both", {f"{MOD}/inputs/a.txt": "a-mine\n", f"{MOD}/GEN.md": "a-mine\nb\n"})
    branch("pr-nogen", {f"{MOD}/inputs/a.txt": "a3\n", f"{MOD}/GEN.md": "a3\nb\n", f"{MOD}/FAIL": "x"})
    g(work, "checkout", "-q", "main")
    write(f"{MOD}/inputs/b.txt", "b2\n"); write(f"{MOD}/GEN.md", "a\nb2\n"); commit("main moves b")
    g(work, "push", "-q", "origin", "main")
    wt = os.path.join(d, "mq-wt-r")

    result = mq.update_over_ssh(work, "pr-gen", wt)
    assert result == "regenerated", result
    g(work, "fetch", "-q", "origin")
    assert g(work, "show", f"origin/pr-gen:{MOD}/GEN.md") == "a2\nb2", "both sides' inputs are in the regenerated product"
    assert g(work, "merge-base", "--is-ancestor", "origin/main", "origin/pr-gen") == "" and not os.path.exists(wt)

    before = g(work, "rev-parse", "origin/pr-both")
    g(work, "checkout", "-q", "main")
    write(f"{MOD}/inputs/a.txt", "a-main\n"); write(f"{MOD}/GEN.md", "a-main\nb2\n"); commit("main moves a")
    g(work, "push", "-q", "origin", "main")
    assert mq.update_over_ssh(work, "pr-both", wt) == "conflict", "a file a person wrote conflicts too: back to the author"
    g(work, "fetch", "-q", "origin")
    assert g(work, "rev-parse", "origin/pr-both") == before and not os.path.exists(wt), "nothing pushed"

    before = g(work, "rev-parse", "origin/pr-nogen")
    assert mq.update_over_ssh(work, "pr-nogen", wt) == "conflict", "build_all.py fails on the merged tree: not settled"
    g(work, "fetch", "-q", "origin")
    assert g(work, "rev-parse", "origin/pr-nogen") == before

    branch("pr-check", {f"{MOD}/inputs/c.txt": "c\\n", f"{MOD}/GEN.md": "a-main\\nb2\\nc\\n", f"{MOD}/CHECKFAIL": "x"})
    g(work, "checkout", "-q", "main")
    write(f"{MOD}/inputs/b.txt", "b3\\n"); write(f"{MOD}/GEN.md", "a-main\\nb3\\n"); commit("main moves b again")
    g(work, "push", "-q", "origin", "main")
    before = g(work, "rev-parse", "origin/pr-check")
    assert mq.update_over_ssh(work, "pr-check", wt) == "conflict", "build_all.py --check fails on the result: not pushed"
    g(work, "fetch", "-q", "origin")
    assert g(work, "rev-parse", "origin/pr-check") == before

    # a symbolic link at a conflicted path is never written through; an untracked file build_all.py leaves behind is not committed blindly
    outside = os.path.join(d, "victim.txt")
    with open(outside, "w") as f:
        f.write("untouched\\n")
    g(work, "checkout", "-q", "main")
    os.symlink(os.path.join(d, "start"), os.path.join(work, MOD, "link"))
    commit("main adds a link"); g(work, "push", "-q", "origin", "main")
    g(work, "checkout", "-q", "-B", "pr-link", "main")
    os.remove(os.path.join(work, MOD, "link")); os.symlink(outside, os.path.join(work, MOD, "link"))   # the merge leaves the pull request's own link in the work tree
    commit("pr moves the link"); g(work, "push", "-q", "origin", "pr-link")
    g(work, "checkout", "-q", "main")
    os.remove(os.path.join(work, MOD, "link")); os.symlink(os.path.join(d, "elsewhere"), os.path.join(work, MOD, "link"))
    commit("main moves the link too"); g(work, "push", "-q", "origin", "main")
    assert mq.update_over_ssh(work, "pr-link", wt) == "conflict" and open(outside).read() == "untouched\\n", "nothing is written through a link"

    branch("pr-log", {f"{MOD}/inputs/c.txt": "c2\\n", f"{MOD}/GEN.md": "a-main\\nb3\\nc2\\n", f"{MOD}/WRITELOG": "x"})
    g(work, "checkout", "-q", "main")
    write(f"{MOD}/inputs/b.txt", "b4\\n"); write(f"{MOD}/GEN.md", "a-main\\nb4\\n"); commit("main moves b once more")
    g(work, "push", "-q", "origin", "main")
    assert mq.update_over_ssh(work, "pr-log", wt) == "conflict", "an untracked file build_all.py wrote: not committed, not pushed"

    # a conflict in a file build_all.py does not generate, next to a generated one
    branch("pr-extra", {f"{MOD}/other.txt": "mine\n", f"{MOD}/inputs/b.txt": "b-mine\n", f"{MOD}/GEN.md": "a-main\nb-mine\n"})
    g(work, "checkout", "-q", "main")
    write(f"{MOD}/other.txt", "main\n"); commit("main touches other")
    g(work, "push", "-q", "origin", "main")
    assert mq.update_over_ssh(work, "pr-extra", wt) == "conflict"


def _remove_busy(mq, d):
    """`remove` of a pull request a worker is processing: refused, or the worker stops before the merge; never a silent carry-on (#574 was taken out of the queue and merged anyway, 2026-10-22)."""
    qpath, lpath = os.path.join(d, "rb", "merge.queue"), os.path.join(d, "rb", "merge.log")
    q = mq.Queue(qpath)
    q.enqueue(300, 301)

    def cli(*args):
        try:
            mq.main(["--queue", qpath, "--log", lpath, *args])
        except SystemExit as error:
            return str(error)
        return None

    # a pull request being processed is refused, and stays queued
    q.mark_working(300)
    message = cli("remove", "300")
    assert message and "cannot be taken back" in message and q.numbers() == [300, 301], (message, q.numbers())
    assert q.working() == {300: os.getpid()}
    # one that is not being processed is removed as before, also together with a refused one: nothing is removed then
    assert cli("remove", "301", "300") is not None and q.numbers() == [300, 301], "a refused remove removes none of them"
    assert cli("remove", "301") is None and q.numbers() == [300]
    # --cancel: out of the queue, cancel requested
    assert cli("remove", "--cancel", "300") is None and q.numbers() == [] and q.cancel_requested(300)
    q.unmark_working(300)
    assert not q.cancel_requested(300) and q.working() == {}, "the worker's end clears marker and request"
    # a marker whose runner is gone is stale: not a reason to refuse
    with open(qpath + ".working", "w") as f:
        f.write("300 999999\n")
    assert q.working() == {}
    q.enqueue(300)
    assert cli("remove", "300") is None and q.numbers() == []

    # the worker stops at its checkpoint, before the merge
    gh = FakeGh(); gh.add(310, rounds=[dict(pending=3, checks=green())])
    holder = {}
    proc, q2, log, slept = _proc(mq, d, "rb2", gh, [310], on_sleep=lambda seconds: holder["q"].request_cancel(310) if seconds == 30 else None)
    holder["q"] = q2
    assert proc.process(310) == "CANCELLED" and "CANCELLED" in events(log) and not any(c[0] == "merge" for c in gh.calls) and "MERGED" not in events(log), events(log)
    assert q2.working() == {} and not q2.cancel_requested(310)

    # the request arrives while the merge is being decided (under the merge lock): the last checkpoint stops it before gh is asked
    gh = FakeGh(); gh.add(313)
    proc, q5, log, _ = _proc(mq, d, "rb5", gh, [313])
    real_behind, calls = gh.behind_by, {"n": 0}

    def behind_then_cancel(n, head):
        calls["n"] += 1
        if calls["n"] == 2:   # the first look is at the start of the round, the second inside merge_when_fresh
            q5.request_cancel(n)
        return real_behind(n, head)
    gh.behind_by = behind_then_cancel
    assert proc.process(313) == "CANCELLED" and not any(c[0] == "merge" for c in gh.calls) and "MERGED" not in events(log), (events(log), gh.calls)

    # too late: the merge call has been made when the request comes
    gh = FakeGh(); gh.add(311)
    proc, q3, log, _ = _proc(mq, d, "rb3", gh, [311])
    real_merge = gh.merge

    def merge_then_cancel(n, head):
        q3.request_cancel(n)
        return real_merge(n, head)
    gh.merge = merge_then_cancel
    assert proc.process(311) == "MERGED" and "CANCELLED" not in events(log) and not q3.cancel_requested(311), events(log)

    # taken out of the queue between the look and the claim: not started
    gh = FakeGh(); gh.add(312)
    proc, q4, log, _ = _proc(mq, d, "rb4", gh, [312])
    real_claim = q4.claim
    q4.claim = lambda n: (q4.remove(n), real_claim(n))[1]
    stops = iter([False, True] + [True] * 20)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert "SKIPPED" in events(log) and "START" not in events(log) and not any(c[0] == "merge" for c in gh.calls) and q4.working() == {} and not proc.busy, events(log)

    # check and removal are one step: claimed first -> refused (or cancelled); removed first -> the claim fails
    q5 = mq.Queue(os.path.join(d, "rb6", "merge.queue"))
    q5.enqueue(320, 321)
    assert q5.claim(320) and q5.remove_checked({320, 321}, False) == {320: os.getpid()} and q5.numbers() == [320, 321]
    assert q5.remove_checked({321}, False) == {} and q5.numbers() == [320]
    assert q5.remove_checked({320}, True) == {320: os.getpid()} and q5.numbers() == [] and q5.cancel_requested(320)
    assert not q5.claim(321) and not q5.claim(320), "removed: the claim fails"
    q5.unmark_working(320)
    # a request for a pull request nobody is processing is not kept (it would cancel its next round); a mark from an earlier round does not carry a request over
    q5.request_cancel(330)
    assert not q5.cancel_requested(330)
    q5.enqueue(330)
    assert q5.claim(330)
    q5.request_cancel(330)
    q5.unmark_working(330)
    q5.enqueue(330)
    assert q5.claim(330) and not q5.cancel_requested(330)
    with open(os.path.join(d, "rb6", "merge.queue.cancel"), "w") as f:
        f.write("332\n")   # left over from a round whose worker is gone
    q5.enqueue(332)
    assert q5.claim(332) and not q5.cancel_requested(332), "a new round does not inherit an old request"
    # the runner's start clears what a killed runner left behind (also a pid that is alive by accident)
    with open(os.path.join(d, "rb6", "merge.queue.working"), "w") as f:
        f.write("331 1\n")
    with open(os.path.join(d, "rb6", "merge.queue.cancel"), "w") as f:
        f.write("331\n")
    q5.reset_working()
    assert q5.working() == {} and not q5.cancel_requested(331)

    # CANCELLED does not take a pull request out that was queued again after `remove --cancel`; the queued label comes off
    gh = FakeGh(); gh.add(340, labels=("queued",), rounds=[dict(pending=3, checks=green())])
    holder = {}
    proc, q6, log, _ = _proc(mq, d, "rb7", gh, [340], on_sleep=lambda seconds: holder["q"].request_cancel(340) if seconds == 30 else None)
    holder["q"] = q6
    stops = iter([False, True] + [True] * 20)
    real_process = proc.process

    def process_then_requeue(n):
        result = real_process(n)
        q6.enqueue(n)
        return result
    proc.process = process_then_requeue
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert "CANCELLED" in events(log) and q6.numbers() == [340] and ("label", 340, "queued", False) in gh.calls, (events(log), q6.numbers(), gh.calls)

    # a claim that fails (disk) leaves nothing busy or marked; a failing unmark still frees busy; a request that came too late (the round ended in MERGED) does not take out an entry queued again meanwhile
    gh = FakeGh(); gh.add(350)
    proc, q12, log, _ = _proc(mq, d, "rb12", gh, [350])
    q12.claim = lambda n: (_ for _ in ()).throw(OSError("disk full"))
    try:
        proc.pick("code")
        raise AssertionError("must raise")
    except OSError:
        pass
    assert proc.busy == set() and q12.working() == {}, "a failed claim leaves nothing busy"
    q12.claim = mq.Queue.claim.__get__(q12)
    q12.unmark_working = lambda n: (_ for _ in ()).throw(OSError("disk full"))
    proc.busy.add(350)
    try:
        proc.release(350)
    except OSError:
        pass
    assert 350 not in proc.busy, "busy is freed even when the mark cannot be removed"
    gh = FakeGh(); gh.add(351)
    holder = {}
    proc, q13, log, _ = _proc(mq, d, "rb13", gh, [351])
    real_merge = gh.merge

    def merge_after_cancel_and_requeue(n, head):
        q13.request_cancel(n)
        q13.remove(n)
        q13.enqueue(n)   # remove --cancel, then queued again, while the merge call is already under way
        return real_merge(n, head)
    gh.merge = merge_after_cancel_and_requeue
    stops = iter([False, True] + [True] * 20)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert "MERGED" in events(log) and q13.numbers() == [351], (events(log), q13.numbers())

    # the request stops it while it waits for CI, before the merge is even looked at (the checkpoint in the CI wait)
    gh = FakeGh(); gh.add(341, rounds=[dict(pending=3, checks=green())])
    holder = {}
    looks = {"n": 0}
    real_behind = gh.behind_by
    gh.behind_by = lambda n, head: (looks.__setitem__("n", looks["n"] + 1), real_behind(n, head))[1]
    proc, q7, log, _ = _proc(mq, d, "rb8", gh, [341], on_sleep=lambda seconds: holder["q"].request_cancel(341) if seconds == 30 else None)
    holder["q"] = q7
    assert proc.process(341) == "CANCELLED" and looks["n"] == 1, looks

    gh = FakeGh(); gh.add(345)
    holder = {}
    proc, q11, log, _ = _proc(mq, d, "rb9b", gh, [345])
    holder["q"] = q11
    real_view = gh.view
    gh.view = lambda n: (holder["q"].request_cancel(n), real_view(n))[1]
    assert proc.process(345) == "CANCELLED" and ("label", 345, "queued", True) not in gh.calls, gh.calls

    # while it waits for the checks to be listed (before the CI wait proper)
    gh = FakeGh(); gh.add(344)
    real_checks = gh.checks
    polls = {"n": 0}
    holder = {}

    def light_only(n):
        polls["n"] += 1
        return green(light=True) if polls["n"] <= 30 else real_checks(n)
    gh.checks = light_only
    proc, q10, log, _ = _proc(mq, d, "rb11", gh, [344], on_sleep=lambda seconds: holder["q"].request_cancel(344) if seconds == 15 else None)
    holder["q"] = q10
    assert proc.process(344) == "CANCELLED" and polls["n"] <= 3, polls

    # a request before the branch is updated or labelled stops it there (anything pushed to the branch costs a CI run)
    gh = FakeGh(); gh.add(342, merge="BEHIND")
    holder = {}
    proc, q8, log, _ = _proc(mq, d, "rb9", gh, [342])
    holder["q"] = q8
    real_view = gh.view
    gh.view = lambda n: (holder["q"].request_cancel(n), real_view(n))[1]
    assert proc.process(342) == "CANCELLED" and not any(c[0] == "update-branch" for c in gh.calls) and ("label", 342, "queued", True) not in gh.calls, gh.calls

    # `once` (no pick) marks the pull request while it is processed
    gh = FakeGh(); gh.add(343, rounds=[dict(pending=1, checks=green())])
    seen = []
    proc, q9, log, _ = _proc(mq, d, "rb10", gh, [], on_sleep=lambda seconds: seen.append(dict(holder["q"].working())))
    holder["q"] = q9
    assert proc.process(343) == "MERGED" and any(343 in w for w in seen) and q9.working() == {}, seen


STANDIN_BUILD_ALL = """import os, sys
here = os.path.dirname(os.path.abspath(__file__))
target = os.path.join(here, "EXTRA.md")
if "--check" in sys.argv:
    sys.exit(0 if os.path.exists(target) and open(target, encoding="utf-8").read() == "extra\\n" else "EXTRA.md is not what is generated")
open(target, "w", encoding="utf-8").write("extra\\n")
"""


def _ignored_conflicts(mq, d):
    """A product that has moved out of the repository (untracked and ignored) which the other side still changed: modify/delete, in both directions; settled by taking it out of the index. A real delete, or a conflict in a file a person wrote next to it, is not."""
    def g(cwd, *a):
        r = subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false", *a], cwd=cwd, capture_output=True, text=True)
        assert r.returncode == 0, (a, r.stderr)
        return r.stdout.strip()

    def repo(name):
        origin, work = os.path.join(d, f"{name}.git"), os.path.join(d, name)
        g(d, "init", "-q", "--bare", "-b", "main", origin)
        g(d, "clone", "-q", origin, work)
        os.makedirs(os.path.join(work, MOD))

        def write(path, text):
            with open(os.path.join(work, path), "w", encoding="utf-8") as f:
                f.write(text)

        def commit(message):
            g(work, "add", "-A"); g(work, "commit", "-q", "-m", message)

        write(f"{MOD}/build_all.py", STANDIN_BUILD_ALL); write(f"{MOD}/EXTRA.md", "extra\n"); write(f"{MOD}/human.txt", "h\n")
        commit("base"); g(work, "push", "-q", "origin", "main")
        return work, write, commit

    def untrack(work, write, commit, ignore=True):
        g(work, "rm", "-q", "--cached", f"{MOD}/EXTRA.md")
        if ignore:
            write(".gitignore", f"{MOD}/EXTRA.md\n")
        else:
            os.remove(os.path.join(work, MOD, "EXTRA.md"))
        commit("untrack the product")

    wt = os.path.join(d, "mq-wt-i")

    # main moves the product out, the pull request still changed it
    work, write, commit = repo("ig1")
    g(work, "checkout", "-q", "-B", "pr", "main"); write(f"{MOD}/EXTRA.md", "mine\n"); commit("pr edits the product"); g(work, "push", "-q", "origin", "pr")
    g(work, "checkout", "-q", "main"); untrack(work, write, commit); g(work, "push", "-q", "origin", "main")
    assert mq.update_over_ssh(work, "pr", wt) == "regenerated"
    g(work, "fetch", "-q", "origin")
    assert f"{MOD}/EXTRA.md" not in g(work, "ls-tree", "-r", "--name-only", "origin/pr").splitlines() and g(work, "merge-base", "--is-ancestor", "origin/main", "origin/pr") == ""

    # the pull request moved it out, main changed it
    work, write, commit = repo("ig2")
    g(work, "checkout", "-q", "-B", "pr", "main"); untrack(work, write, commit); g(work, "push", "-q", "origin", "pr")
    g(work, "checkout", "-q", "main"); write(f"{MOD}/EXTRA.md", "main\n"); commit("main edits the product"); g(work, "push", "-q", "origin", "main")
    assert mq.update_over_ssh(work, "pr", wt) == "regenerated"
    g(work, "fetch", "-q", "origin")
    assert f"{MOD}/EXTRA.md" not in g(work, "ls-tree", "-r", "--name-only", "origin/pr").splitlines()

    # a file a person wrote, deleted on one side and changed on the other: not ignored, so a person decides (also: ignoring a human file is not what makes it settle)
    work, write, commit = repo("ig3")
    g(work, "checkout", "-q", "-B", "pr", "main"); write(f"{MOD}/human.txt", "mine\n"); commit("pr edits"); g(work, "push", "-q", "origin", "pr")
    g(work, "checkout", "-q", "main"); g(work, "rm", "-q", f"{MOD}/human.txt"); commit("main deletes human.txt"); g(work, "push", "-q", "origin", "main")
    before = g(work, "rev-parse", "origin/pr")
    assert mq.update_over_ssh(work, "pr", wt) == "conflict"
    g(work, "fetch", "-q", "origin")
    assert g(work, "rev-parse", "origin/pr") == before

    # a HUMAN file wrongly ignored (main took it out of the index and ignored it by mistake): the pull request's change would be lost, so it is never settled here
    work, write, commit = repo("ig5")
    g(work, "checkout", "-q", "-B", "pr", "main"); write(f"{MOD}/human.txt", "mine\n"); commit("pr edits the human file"); g(work, "push", "-q", "origin", "pr")
    g(work, "checkout", "-q", "main"); g(work, "rm", "-q", "--cached", f"{MOD}/human.txt"); write(".gitignore", f"{MOD}/human.txt\n"); commit("main ignores the human file"); g(work, "push", "-q", "origin", "main")
    before = g(work, "rev-parse", "origin/pr")
    assert mq.update_over_ssh(work, "pr", wt) == "conflict"
    g(work, "fetch", "-q", "origin")
    assert g(work, "rev-parse", "origin/pr") == before

    # ignored only by this machine's own exclude file (not by a .gitignore of the tree): not a moved-out product
    work, write, commit = repo("ig6")
    g(work, "checkout", "-q", "-B", "pr", "main"); write(f"{MOD}/EXTRA.md", "mine\n"); commit("pr edits"); g(work, "push", "-q", "origin", "pr")
    g(work, "checkout", "-q", "main"); untrack(work, write, commit, ignore=False); g(work, "push", "-q", "origin", "main")
    with open(os.path.join(work, ".git", "info", "exclude"), "a") as f:
        f.write(f"{MOD}/EXTRA.md\n")
    assert mq.moved_out_conflicts.__name__ == "moved_out_conflicts"
    result = mq.update_over_ssh(work, "pr", wt)
    g(work, "fetch", "-q", "origin")
    assert result == "regenerated" and f"{MOD}/EXTRA.md" in g(work, "ls-tree", "-r", "--name-only", "origin/pr").splitlines(), "regenerated like any product, but it stays tracked: the machine's exclude file is no reason to take it out of the index"

    # .gitignore itself conflicting: what is ignored is not known yet
    work, write, commit = repo("ig7")
    g(work, "checkout", "-q", "-B", "pr", "main"); write(".gitignore", "pr-line\n"); write(f"{MOD}/EXTRA.md", "mine\n"); commit("pr edits both"); g(work, "push", "-q", "origin", "pr")
    g(work, "checkout", "-q", "main"); write(".gitignore", "main-line\n"); commit("main edits .gitignore"); untrack(work, write, commit); g(work, "push", "-q", "origin", "main")
    assert mq.update_over_ssh(work, "pr", wt) == "conflict"

    # a link in place of the moved product is never written through, also when it is on its way out of the repository
    outside = os.path.join(d, "ig8-victim.txt")
    with open(outside, "w") as f:
        f.write("untouched\n")
    work, write, commit = repo("ig8")
    g(work, "checkout", "-q", "-B", "pr", "main"); os.remove(os.path.join(work, MOD, "EXTRA.md")); os.symlink(outside, os.path.join(work, MOD, "EXTRA.md")); commit("pr turns the product into a link"); g(work, "push", "-q", "origin", "pr")
    g(work, "checkout", "-q", "main"); untrack(work, write, commit); g(work, "push", "-q", "origin", "main")
    assert mq.update_over_ssh(work, "pr", wt) == "conflict" and open(outside).read() == "untouched\n"

    # a file a person wrote conflicts next to the moved product
    work, write, commit = repo("ig4")
    g(work, "checkout", "-q", "-B", "pr", "main"); write(f"{MOD}/EXTRA.md", "mine\n"); write(f"{MOD}/human.txt", "mine\n"); commit("pr edits both"); g(work, "push", "-q", "origin", "pr")
    g(work, "checkout", "-q", "main"); write(f"{MOD}/human.txt", "main\n"); commit("main edits human.txt"); untrack(work, write, commit); g(work, "push", "-q", "origin", "main")
    before = g(work, "rev-parse", "origin/pr")
    assert mq.update_over_ssh(work, "pr", wt) == "conflict"
    g(work, "fetch", "-q", "origin")
    assert g(work, "rev-parse", "origin/pr") == before


def _gapscan(mq, d):
    import datetime
    import gapscan
    now = datetime.datetime(2026, 10, 22, 20, 0, tzinfo=datetime.timezone.utc)

    def at(minutes_ago):
        return (now - datetime.timedelta(minutes=minutes_ago)).isoformat().replace("+00:00", "Z")
    prefixes = {"claude/one-a": "one-a", "claude/one-a-b": "one-a-b"}

    def pr(n, branch="claude/one-a-x", head="a" * 40, idle=5, mergeable="MERGEABLE", rollup=(), comments=(), draft=False):
        return dict(number=n, title=f"pull request {n}", headRefName=branch, headRefOid=head, isDraft=draft, mergeable=mergeable, statusCheckRollup=list(rollup),
                    commits=[dict(committedDate=at(idle))], comments=list(comments), createdAt=at(idle))

    def check(name, conclusion, started):
        return dict(name=name, workflowName="ci", conclusion=conclusion, startedAt=at(started))
    approval = lambda head, minutes=1, author=gapscan.APPROVER: dict(author=author, createdAt=at(minutes), body=f"调度批准入队 head {head}")

    assert gapscan.approved_head([dict(author=gapscan.APPROVER, createdAt=at(1), body="调度批准入队 head ABCDEF123")], "abcdef123" + "0" * 31), "a sha in capitals is the same sha"
    assert gapscan.author_session("claude/one-a-b-thing", prefixes) == "one-a-b" and gapscan.author_session("claude/one-a-thing", prefixes) == "one-a"
    assert gapscan.author_session("claude/one-ab", prefixes) == "?(claude/one-ab)", "a prefix must end at a dash"
    assert gapscan.author_session("claude/nobody", prefixes) == "?(claude/nobody)"

    assert gapscan.red_checks([check("backend verify", "FAILURE", 30), check("backend verify", "SUCCESS", 10)]) == [], "a rerun that passed replaced the red run"
    assert gapscan.red_checks([check("backend verify", "SUCCESS", 30), check("backend verify", "FAILURE", 10)]) == ["backend verify"]
    assert gapscan.red_checks([check("backend verify", "FAILURE", 30), check("backend verify", "CANCELLED", 10)]) == ["backend verify"], "a cancelled newer run does not clear a red one"
    assert gapscan.red_checks([check("backend verify", "FAILURE", 30), dict(check("backend verify", "", 10), status="IN_PROGRESS")]) == [], "a rerun in progress replaces the red run"
    assert gapscan.red_checks([check("frontend verify", "CANCELLED", 10)]) == [], "a cancelled run says nothing"
    assert gapscan.red_checks([check("assertion drift (warning only)", "FAILURE", 10), check("backend verify (light)", "FAILURE", 10)]) == [], "only the required checks, and not the light run of the docs lane"
    assert gapscan.red_checks([check("backend verify (Modulith + ArchUnit)", "FAILURE", 10)]) == ["backend verify (Modulith + ArchUnit)"], "the long job name starts with the required one"
    assert gapscan.red_checks([dict(context="secret scan", state="ERROR")]) == ["secret scan"], "a commit status has context/state instead of name/conclusion"
    assert gapscan.red_checks([dict(context="secret scan", state="SUCCESS")]) == []

    head = "b" * 40
    prs = [pr(1, mergeable="CONFLICTING"), pr(2, rollup=[check("backend verify", "FAILURE", 10)]), pr(3, head=head, comments=[approval(head[:9])]),
           pr(4, idle=61), pr(5, idle=59), pr(6, idle=600, draft=True), pr(7, idle=600, mergeable="CONFLICTING"), pr(8, head=head, comments=[approval("c" * 9)]),
           pr(9, head=head, comments=[approval(head[:9], author="somebody")]), pr(10, head=head, comments=[approval("c" * 9, 20), approval(head[:9], 5)]),
           pr(11, head=head, comments=[approval(head[:9], 20), approval("c" * 9, 5)])]
    gaps = gapscan.find_gaps(prs, {7}, now, prefixes)
    got = sorted((g["pr"], g["reason"]) for g in gaps)
    assert got == [(1, "CONFLICT"), (2, "CI_RED"), (3, "APPROVED_NOT_QUEUED"), (4, "STALE"), (10, "APPROVED_NOT_QUEUED")], got
    assert all(g["session"] == "one-a" for g in gaps)

    state = {}
    first = gapscan.select_new(gaps, state, now)
    assert len(first) == 5
    assert gapscan.select_new(gaps, state, now + datetime.timedelta(hours=3, minutes=59)) == [], "the same reason is reported once in 4 hours"
    assert len(gapscan.select_new(gaps, state, now + datetime.timedelta(hours=4))) == 5
    # a gap that is gone for a round (mergeable=UNKNOWN right after a merge into main, the list cut off, the queue taking the pull request for a while) is not new when it is back inside the window
    flicker = [g for g in gaps if g["reason"] != "CONFLICT"]
    gapscan.select_new(flicker, state, now + datetime.timedelta(hours=4, minutes=30))
    assert "#1:CONFLICT" in state, "the key is kept while its window runs"
    assert gapscan.select_new(gaps, state, now + datetime.timedelta(hours=5)) == [], "it came back inside the window: reported once per window, not on every flicker"
    assert len(gapscan.select_new(gaps, state, now + datetime.timedelta(hours=8, minutes=31))) == 5 and set(state) == {f"#{g['pr']}:{g['reason']}" for g in gaps}, "a window that ran out reports again and keeps nothing stale"
    stale = {"#99:STALE": at(60 * 6)}
    gapscan.select_new([], stale, now)
    assert stale == {}, "a key whose window ran out goes"
    broken = {"#1:CONFLICT": 12345}
    assert len(gapscan.select_new(gaps, broken, now)) == 5, "a state value that is not a time is no report"
    assert {g["pr"] for g in gapscan.select_new(gaps, {}, now)} == {1, 2, 3, 4, 10}

    class Listing:
        def __init__(self, rows):
            self.rows = rows

        def open_prs(self):
            return self.rows
    directory = os.path.join(d, "gapscan")
    q = mq.Queue(os.path.join(directory, "merge.queue"))
    q.enqueue(7)
    log = mq.Log(os.path.join(directory, "merge.log"), echo=False)
    fresh = gapscan.scan(Listing(prs), q, directory, log, now=now, prefixes=prefixes)
    assert len(fresh) == 5 and sum(1 for l in log.lines if " GAPS " in l) == 1, log.lines
    text = open(os.path.join(directory, "gaps.md"), encoding="utf-8").read()
    assert "* #1 [one-a] CONFLICT" in text and "#7" not in text and "#6" not in text and text.count("* #") == 5, text
    log_line = [l for l in log.lines if " GAPS " in l][0]
    assert "5 new of 5" in log_line and "gaps.md" in log_line, log_line
    assert gapscan.scan(Listing(prs), q, directory, log, now=now, prefixes=prefixes) == []
    assert sum(1 for l in log.lines if " GAPS " in l) == 1, "nothing new: no second line"
    before = open(os.path.join(directory, "gaps.state.json"), encoding="utf-8").read()
    assert gapscan.scan(Listing(None), q, directory, log, now=now, prefixes=prefixes) is None and any(" ERROR " in l and "gap scan" in l for l in log.lines)
    assert open(os.path.join(directory, "gaps.state.json"), encoding="utf-8").read() == before, "gh failing must not forget what was reported"

    # the thread: scans on its own clock whether or not the queue has anything, and survives a failing scan
    class Boom(Listing):
        calls = 0

        def open_prs(self):
            Boom.calls += 1
            if Boom.calls == 1:
                raise RuntimeError("boom")
            return []
    slept, stop_after = [], []
    proc = mq.Processor(Boom([]), q, log, "/repo", sleep=lambda s: slept.append(s))
    mq.run_scan(proc, directory, every=1800, first=60, stop=lambda: len(slept) > 3)
    assert slept[:3] == [60, 1800, 1800] and Boom.calls == 3, (slept, Boom.calls)
    assert any("gap scan: RuntimeError: boom" in l for l in log.lines)

    # the mapping file names real prefixes only and every session profile has one
    mapped = gapscan.load_prefixes()
    profiles = {f[:-3] for f in os.listdir(os.path.join(mq.HERE, "..", "..", "docs", "modernization", "sessions")) if f.startswith("one-") and f.endswith(".md")}
    assert profiles <= set(mapped.values()), profiles - set(mapped.values())


def _head_changed(mq, d):
    """GOV-1022-MRG-06: a head that is not the one approved at enqueue is never merged; the pull request goes out of the queue with a HEAD-CHANGED line (both shas, owner)."""
    approved = f"{1:040x}"

    # unchanged head: merged as before, nothing recorded stays
    gh = FakeGh(); gh.add(1)
    proc, q, log, _ = _proc(mq, d, "h1", gh, [1])
    q.set_head(1, approved)
    assert proc.process(1) == "MERGED" and "HEAD-CHANGED" not in events(log), events(log)

    # pushed after the approval, before the queue took it: not merged, no label, no update, out of the queue by the lane
    gh = FakeGh(); gh.add(2, merge="BEHIND", behind=1)
    proc, q, log, _ = _proc(mq, d, "h2", gh, [2])
    q.set_head(2, f"{2:040x}")
    gh.prs[2]["head"] = "f" * 40
    assert proc.process(2) == "HEAD-CHANGED", events(log)
    assert not any(c[0] in ("merge", "update-branch") or (c[0] == "label" and c[3]) for c in gh.calls), gh.calls
    line = [l for l in log.lines if " HEAD-CHANGED " in l][0]
    assert f"approved {2:040x}"[:18] in line and "now fffffffff" in line, line
    assert q.head_of(2) is None, "the approval is spent"

    # the lane takes it out of the queue as for any other outcome
    gh = FakeGh(); gh.add(3)
    proc, q, log, _ = _proc(mq, d, "h3", gh, [3])
    q.set_head(3, f"{3:040x}")
    gh.prs[3]["head"] = "e" * 40
    proc.queue.numbers()
    mq.run_lane(proc, "code", stop=lambda: not q.numbers())
    assert q.numbers() == [] and "HEAD-CHANGED" in events(log) and "MERGED" not in events(log) and ("merge", 3, True, "e" * 40) not in gh.calls, events(log)

    # pushed while the CI ran: the checks go green, the merge must still not happen
    gh = FakeGh(); gh.add(4, rounds=[dict(pending=2, checks=green())])
    pushes = []

    def author_pushes(seconds):
        if not pushes and gh.prs[4]["state"] == "OPEN":
            pushes.append(1)
            gh.prs[4]["head"] = "d" * 40
    proc, q, log, _ = _proc(mq, d, "h4", gh, [4], on_sleep=author_pushes)
    q.set_head(4, f"{4:040x}")
    assert proc.process(4) == "HEAD-CHANGED" and not any(c[0] == "merge" for c in gh.calls), (events(log), gh.calls)

    # the queue's own push (gh update-branch) changes the head too: that is not the author
    gh = FakeGh(); gh.add(5, merge="BEHIND", behind=3)
    proc, q, log, _ = _proc(mq, d, "h5", gh, [5])
    q.set_head(5, f"{5:040x}")
    assert proc.process(5) == "MERGED" and "UPDATE" in events(log) and "HEAD-CHANGED" not in events(log), events(log)
    # a runner restarted after the queue's update has no memory of it: the head the queue left is on disk, so the push is still not taken for the author's
    assert q.head_of(5) == gh.prs[5]["head"] != f"{5:040x}", q.head_of(5)
    proc2, _, _, _ = _proc(mq, d, "h5", gh, [5])
    assert proc2.own_head == {} and proc2.head_moved(5, gh.view(5)) is None

    # gh cannot say the head when queueing: the older approval of that number is dropped, not left to be compared
    gh = FakeGh(); gh.add(7)
    q = mq.Queue(os.path.join(d, "h7", "merge.queue")); q.enqueue(7); q.set_head(7, f"{7:040x}")
    real_gh = mq.Gh
    mq.Gh = lambda: type("Silent", (), {"view": lambda self, n: None})()
    try:
        with contextlib.redirect_stderr(open(os.devnull, "w")):
            mq.record_heads(q, [7])
    finally:
        mq.Gh = real_gh
    assert q.head_of(7) is None, "an old approval must not outlive a failed read"

    # no approval on record (queued before this, gh could not say, `once`): nothing to compare
    gh = FakeGh(); gh.add(6)
    proc, q, log, _ = _proc(mq, d, "h6", gh, [6])
    gh.prs[6]["head"] = "c" * 40
    assert proc.process(6) == "MERGED"

    # a second `enqueue` is a new approval of the head it has now
    q.set_head(7, "1" * 40); q.set_head(7, "2" * 40)
    assert q.head_of(7) == "2" * 40 and q.head_of(8) is None
    q.drop_head(7)
    assert q.head_of(7) is None
