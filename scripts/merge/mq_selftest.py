"""Self-test of mq.py: a fake gh object for the queue's decisions, a fake `gh` binary for the real Gh class, real git for the ssh update, patch-id and the main checkout,
real processes for the queue lock. Every check keeps at least one case where it must refuse. Not run on its own: `mq.py --self-test`."""
import contextlib
import importlib.util
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
import time
from unittest import mock

import driftscan_selftest
import gapscan_selftest
import mq_acceptance_selftest
import mq_detail_selftest
import mq_label_selftest
import mq_waiting_selftest
import mq_title_selftest

LOG_LINE = re.compile(r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d[+-]\d\d:\d\d [A-Z-]+ pr=#(\d+|-) run=(\d+|-) elapsed=\d+s( .*)?$")
HEAVY = "backend verify (Modulith + ArchUnit guardrails + integration tests)", "frontend verify (typecheck + lint guardrails F1-F14 + structure + build)"
SECRET = "secret scan (gitleaks)"
PID = "5" * 40   # the patch-id every commit of a fake pull request has unless set otherwise


def link(run):
    return f"https://github.com/o/r/actions/runs/{run}/job/1"


def green(run=100, light=False):
    return [{"name": n + (" (light)" if light else ""), "bucket": "pass", "link": link(run)} for n in HEAVY] + [{"name": SECRET, "bucket": "pass", "link": link(run)}]


def red(run=100, bucket="fail"):
    return [{"name": HEAVY[0], "bucket": bucket, "link": link(run)}, {"name": HEAVY[1], "bucket": "pass", "link": link(run)}, {"name": SECRET, "bucket": "pass", "link": link(run)}]


def review(commit, verdict="通过", kind="首轮", at="2026-10-29T01:00:00Z", on=None, stated=PID):
    return {"body": f"复审结论：{verdict}\ncommit：{commit}\n复审者：opus-{kind}-t\n\npatch-id：{stated}\n影响面：x", "commit": on or commit, "at": at}


def approval(sha, at="2026-10-29T02:00:00Z", author="YeliangDu"):
    return {"author": author, "body": f"调度批准入队 head {sha}", "at": at}


class FakeGh:
    def __init__(self):
        self.prs, self.calls, self.runs, self.main_runs, self.lock, self.slow, self.step_failures, self.docker_down = {}, [], {}, {"push": None, "schedule": None}, threading.RLock(), False, {}, 0
        self.merged_behind = []

    def add(self, n, merge="CLEAN", labels=(), rounds=None, update_ok=True, merge_results=((True, ""),), behind=0, state="OPEN"):
        head = f"{n:040x}"
        self.prs[n] = dict(state=state, merge=merge, labels=set(labels), branch=f"b{n}", head=head, behind=behind, rounds=rounds or [dict(pending=0, checks=green())], round=0, pend=None,
                           update_ok=update_ok, merge_results=list(merge_results), reviews=[review(head)], comments=[], pids={}, merges={}, title=f"fix(q): 修复（#{n}）", body=f"Closes #{n}\n")
        return self.prs[n]

    def view(self, n):
        p = self.prs[n]
        return {"state": p["state"], "merge": p["merge"], "labels": sorted(p["labels"]), "branch": p["branch"], "head": p["head"], "title": p["title"], "body": p["body"]}

    def behind_by(self, n, head):
        if self.slow:
            time.sleep(0.02)   # widens the window between looking and merging, so that a missing merge lock shows
        return self.prs[n]["behind"]

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
        return next((p.get("behind_files") for p in self.prs.values() if p["head"] == head), None)

    def files(self, n):
        return self.prs[n].get("files", [])

    def update_branch(self, n):
        self.calls.append(("update-branch", n))
        p = self.prs[n]
        if p["update_ok"]:
            new = p["head"][:-2] + ("dd" if p["head"].endswith("ee") else "ee")
            p["merges"][new] = p["head"]   # the merge commit of main into the head, as gh update-branch makes it
            p["merge"], p["behind"], p["head"] = "CLEAN", 0, new
        return p["update_ok"]

    def label(self, n, name, add):
        self.calls.append(("label", n, name, add))
        (self.prs[n]["labels"].add if add else self.prs[n]["labels"].discard)(name)
        return True
    def labelled(self, name):
        return [n for n, p in self.prs.items() if name in p["labels"]]

    def active_runs(self, branch):
        return list(self.runs.get(branch, []))

    def ci_started(self, branch, head):
        p = next(p for p in self.prs.values() if p["branch"] == branch)
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
        self.docker_down -= 1
        return self.docker_down < 0

    def failed_steps(self, run):
        return self.step_failures.get(str(run), [])

    def rerun(self, run):
        self.calls.append(("rerun", run))
        for p in self.prs.values():
            p["round"], p["pend"] = p["round"] + 1, None
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
            if ok:
                self.merged_behind.append(p["behind"])
                p["state"], p["merged"] = "MERGED", head
                for o, q in self.prs.items():
                    if o != n and q["state"] == "OPEN":
                        q["behind"] += 1   # main moved
            return ok, msg

    def reviews(self, n):
        return self.prs[n]["reviews"]

    def comments(self, n):
        return self.prs[n]["comments"]

    def main_merge_parent(self, repo, n, commit):
        return self.prs[n]["merges"].get(commit)

    def patch_id(self, repo, n, commit):
        self.calls.append(("patch-id", n, commit))
        return self.prs[n]["pids"].get(commit, PID)

    def readback(self, n):
        p = self.prs[n]
        return p["readback"] if "readback" in p else (p["state"], ["m" * 40, p["merged"]] if p.get("merged") else [])


def run(mq):
    env = {"GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_SYSTEM": "/dev/null"}
    old = {k: os.environ.get(k) for k in [*env, "PATH", "FAKE_GH_LOG"]}
    os.environ.update(env)
    try:
        with tempfile.TemporaryDirectory() as d:
            fake_gh(d, [])   # nothing in the self-test reaches the real gh: an unknown call exits 97
            for case in (_units, _flow, _release, _pause, _runner, _queue, _remove_busy, _head_changed, _gh_commands, _git, _docs_tiers, mq_label_selftest.run, mq_waiting_selftest.run, mq_detail_selftest.run, mq_title_selftest.run, mq_acceptance_selftest.run, gapscan_selftest.run, driftscan_selftest.run_with_mq):
                case(mq, d)
    finally:
        for k, v in old.items():
            os.environ.pop(k, None) if v is None else os.environ.__setitem__(k, v)


def fake_gh(d, rules):
    """A `gh` first on PATH that logs its arguments and answers by prefix: rules = [(argv prefix, stdout, stderr, exit code)]."""
    directory = os.path.join(d, "fakegh")
    os.makedirs(directory, exist_ok=True)
    with open(os.path.join(directory, "gh"), "w") as f:
        f.write("#!/usr/bin/env python3\nimport json, os, sys\nargv = sys.argv[1:]\nopen(os.environ['FAKE_GH_LOG'], 'a').write(json.dumps(argv) + '\\n')\n"
                f"rules = json.loads({json.dumps(json.dumps(rules))})\n"
                "for prefix, out, err, rc in rules:\n    if argv[:len(prefix)] == prefix:\n        sys.stdout.write(out); sys.stderr.write(err); sys.exit(rc)\nsys.exit(97)\n")
    os.chmod(os.path.join(directory, "gh"), 0o755)
    if not os.environ["PATH"].startswith(directory):
        os.environ["PATH"] = directory + os.pathsep + os.environ["PATH"]
    os.environ["FAKE_GH_LOG"] = os.path.join(d, "fakegh.log")
    open(os.environ["FAKE_GH_LOG"], "w").close()


def _proc(mq, d, name, gh, queue_numbers=(), lane=None, on_sleep=None, **kw):
    q = mq.Queue(os.path.join(d, name, "merge.queue"))
    q.enqueue(*queue_numbers)
    log = mq.Log(os.path.join(d, name, "merge.log"), echo=False)
    slept = []

    def sleep(seconds):
        slept.append(seconds)
        if on_sleep:
            on_sleep(seconds)
    kw.setdefault("updater", lambda repo, branch, wt: "ok")
    kw.setdefault("docs_only_fn", lambda files: bool(files) and all(f.startswith("docs/") and not f.startswith("docs/guarded/") for f in files))
    return mq.Processor(gh, q, log, "/repo", sleep=sleep, lane=lane or (lambda g, n: "code"), **kw), q, log, slept


def once(mq, d, name, gh, n, **kw):
    proc, q, log, slept = _proc(mq, d, name, gh, [n], **kw)
    return proc.process(n), events(log), log, slept


def events(log):
    return [l.split()[1] for l in log.lines]


def merged(gh):
    return [c for c in gh.calls if c[0] == "merge" and c[2]]


def _units(mq, d):
    ok, why = mq.required_state(green())
    assert ok and not why
    for bad in (green(light=True), [c for c in green() if c["name"] != SECRET], [dict(c, bucket="skipping") for c in green()], [dict(c, bucket="pending") for c in green()], []):
        assert not mq.required_state(bad)[0], bad
    assert "secret scan: not listed" in mq.required_state(green()[:2])[1]
    assert mq.required_state(green() + [{"name": "assertion drift", "bucket": "fail", "link": ""}])[0] and mq.required_state(green(light=True) + green())[0], "only the three full checks decide"
    # a label other than `queued` starts a run whose jobs all skip; GitHub lists their checks under the unexpanded name template (#808, 2026-10-29)
    unexpanded = [{"name": n + "${{ (((github.event_name == 'pull_request') && ' (light)') || '') }}", "bucket": "skipping", "link": link(99)} for n in (*HEAVY, SECRET)]
    assert mq.required_state(green() + unexpanded)[0], "a skipped label run's template-named checks are no verdict"
    assert not mq.required_state(unexpanded)[0] and "backend verify: not listed" in mq.required_state(unexpanded)[1], "nor do they stand in for the real checks"
    r = mq.sh([sys.executable, "-c", "import time; time.sleep(300)"], timeout=1)
    assert r.returncode == 124 and "timed out" in r.stderr, "a hanging call is a failed call"

    # release_verdict: the newest statement that speaks for the head decides
    h, other = "a" * 40, "b" * 40
    pid = {h: PID, other: PID, "c" * 9: PID, "d" * 9: "6" * 40, "e" * 40: "6" * 40}
    rv = lambda reviews, comments=(), head=h, pids=pid: mq.release_verdict(reviews, list(comments), head, lambda c: pids.get(c))
    assert rv([review(h)])[0] and rv([review(other)])[0], "on the head itself, or on another commit with the same patch-id"
    for name, reviews, comments in (("none", [], []), ("必改 is newest", [review(h), review(h, "必改", at="2026-10-29T03:00:00Z")], []),
                                    ("posted on another commit than its header names", [review(h, on=other)], []), ("patch-id differs", [review("e" * 40)], []),
                                    ("final 通过 alone is no release", [review(h, kind="终审")], []), ("final 必改 blocks", [review(h), review(h, "必改", "终审", at="2026-10-29T03:00:00Z")], []),
                                    ("the patch-id the body states is not taken", [review("e" * 40, stated=PID)], []), ("not a header", [dict(review(h), body="LGTM")], []),
                                    ("approval by somebody else", [], [approval(h, author="other")]), ("approval of a different change", [], [approval("d" * 9)]),
                                    ("approval older than a 必改 on the head", [review(h, "必改", at="2026-10-29T03:00:00Z")], [approval(h)])):
        assert not rv(reviews, comments)[0], name
    assert not rv([review(other)], pids={h: None, other: None})[0], "git cannot say: no patch-id match"
    assert not rv([], [approval("e" * 9)], pids={h: None})[0], "an approval whose patch-id git cannot compute does not match a head git cannot compute either"
    assert rv([review(h, "必改")], [approval(h[:9])])[0] and rv([review("e" * 40, "必改")], [approval("c" * 9)])[0], "the coordinator's approval of the fix, by name or by patch-id"
    assert PID[:6] in rv([])[1], "the refusal says the head's patch-id"
    lead_rv = lambda reviews, comments=(): mq.release_verdict(reviews, list(comments), h, lambda c: pid.get(c), approvals_count=False)
    assert not lead_rv([review(h, "必改")], [approval(h)])[0] and lead_rv([], [approval(h)])[0] is None, "lead's own pull request: its approval does not count (#856)"
    assert lead_rv([review(h)], [approval(h)])[0], "a first-round 通过 still releases it"


def _flow(mq, d):
    # clean path: behind -> gh update-branch -> label -> green -> merged pinned to the updated head, read back; the others' CI cancelled; one log line per event
    gh = FakeGh(); gh.add(1, merge="BEHIND", behind=2); gh.add(2); gh.runs["b2"] = [555, 556]
    proc, q, log, _ = _proc(mq, d, "a", gh, [1, 2])
    assert proc.process(1) == "MERGED" and events(log) == ["START", "CANCEL-RUN", "CANCEL-RUN", "UPDATE", "LABEL-QUEUED", "MERGED"], events(log)
    assert ("cancel", 555) in gh.calls and merged(gh) == [("merge", 1, True, f"{1:040x}"[:-2] + "ee")] and all(LOG_LINE.match(l) for l in log.lines)
    assert "pr=#2 run=555" in log.lines[1] and log.lines[-1].split()[3] == "run=100" and open(os.path.join(d, "a", "merge.log")).read().splitlines() == log.lines
    gh = FakeGh(); gh.add(3, behind=4)
    assert once(mq, d, "a2", gh, 3)[0] == "MERGED" and ("update-branch", 3) in gh.calls, "behind by commit count while GitHub says CLEAN: updated anyway"

    # gh cannot update: the ssh update; its conflict is CONFLICT, its failure HOLD; a conflicting pull request is settled the same way, and GitHub may see the push late
    gh = FakeGh(); gh.add(4, merge="BEHIND", update_ok=False)
    result, ev, _, _ = once(mq, d, "b", gh, 4, updater=lambda repo, branch, wt: (gh.prs[4].update(merge="CLEAN", behind=0), "ok")[1])
    assert result == "MERGED" and "SSH-UPDATE" in ev and "UPDATE" not in ev, ev
    for merge, res, outcome in (("BEHIND", "conflict", "CONFLICT"), ("BEHIND", "error", "HOLD"), ("DIRTY", "conflict", "CONFLICT"), ("DIRTY", "error", "HOLD"), ("DIRTY", "ok", "CONFLICT")):
        gh = FakeGh(); gh.add(5, merge=merge, update_ok=False)
        assert once(mq, d, f"b-{merge}-{res}", gh, 5, updater=lambda repo, branch, wt, r=res: r)[0] == outcome and not merged(gh), (merge, res)
    gh = FakeGh(); gh.add(6, merge="DIRTY")
    real_view, looks = gh.view, []
    gh.view = lambda n: (looks.append(1), real_view(n), len(looks) >= 3 and gh.prs[n].update(merge="CLEAN"))[1]
    assert once(mq, d, "b-late", gh, 6)[0] == "MERGED", "GitHub sees the settled push a little later"

    # closed, or gh cannot say: HOLD
    gh = FakeGh(); gh.add(7, state="CLOSED")
    assert once(mq, d, "c1", gh, 7)[0] == "HOLD" and gh.calls == []
    gh = FakeGh(); gh.add(8); gh.view = lambda n: None
    assert once(mq, d, "c2", gh, 8)[0] == "HOLD" and not merged(gh)

    # a red check is rerun once; red again is HOLD with both runs; a cancelled check is red; unknown failed steps: rerun, said out loud
    gh = FakeGh(); gh.add(9, rounds=[dict(pending=1, checks=red(777)), dict(pending=0, checks=green(778))])
    result, ev, log, _ = once(mq, d, "d1", gh, 9)
    assert result == "MERGED" and ("rerun", "777") in gh.calls and ev.count("RERUN-FAILED") == 1 and log.lines[-1].split()[3] == "run=778"
    gh = FakeGh(); gh.add(10, rounds=[dict(pending=0, checks=red(9)), dict(pending=0, checks=red(1000))])
    result, ev, log, _ = once(mq, d, "d2", gh, 10)
    assert result == "HOLD" and [c for c in gh.calls if c[0] == "rerun"] == [("rerun", "9")] and not merged(gh) and "run=1000" in log.lines[-1]
    gh = FakeGh(); gh.add(11, rounds=[dict(pending=0, checks=red(1, "cancel")), dict(pending=0, checks=green(9))])
    assert once(mq, d, "d3", gh, 11)[0] == "MERGED" and ("rerun", "1") in gh.calls
    gh = FakeGh(); gh.add(12, rounds=[dict(pending=0, checks=red(51)), dict(pending=0, checks=green(52))]); gh.failed_steps = lambda run: None
    assert "FAILED-STEPS-UNKNOWN" in once(mq, d, "d4", gh, 12)[1] and ("rerun", "51") in gh.calls

    # the environment, not the pull request: only environment steps red and docker really down -> wait, run again, no attempt used; never recovers -> HOLD naming it
    for name, steps, down, rounds, outcome, env in (("recovers", {"61": ["test images are present"]}, 3, [red(61), red(62), green(63)], "MERGED", True),
                                                    ("never", {"71": ["docker is running"]}, 10 ** 6, [red(71)], "HOLD", True),
                                                    ("docker fine", {"91": ["test images are present"]}, 0, [red(91), green(92)], "MERGED", False),
                                                    ("a real step too", {"93": ["docker is running", "surefire"]}, 10 ** 6, [red(93), green(94)], "MERGED", False),
                                                    ("one run real", {"95": ["docker is running"], "96": ["surefire"]}, 10 ** 6, [red(95) + red(96), green(97)], "MERGED", False)):
        gh = FakeGh(); gh.add(13, rounds=[dict(pending=0, checks=c) for c in rounds]); gh.docker_down, gh.step_failures = down, steps
        result, ev, log, _ = once(mq, d, "env-" + name, gh, 13)
        assert result == outcome and ("ENV" in ev) == env, (name, ev)
    gh = FakeGh(); gh.add(14, rounds=[dict(pending=0, checks=red(81))]); gh.step_failures = {"81": ["test images are present"]}
    flips = iter([False, True] * 20)   # down when judged, up when waited for
    gh.docker_ok = lambda: next(flips)
    result, ev, log, _ = once(mq, d, "env-flips", gh, 14)
    assert result == "HOLD" and ev.count("ENV") == 4 and "environment" in log.lines[-1], "an environment that keeps failing is not rerun for ever"

    # others' runs are cancelled again while the head waits, not those of a pull request another lane holds, nor of an urgent one; urgent is picked first
    gh = FakeGh(); gh.add(15, rounds=[dict(pending=6, checks=green())]); gh.add(16); gh.add(17); gh.add(18, labels=[mq.URGENT_LABEL]); gh.runs.update(b16=[900], b17=[950], b18=[980])
    proc, q, log, _ = _proc(mq, d, "f", gh, [15, 16, 17, 18])
    proc.busy.add(17)
    real_checks, polls = gh.checks, []
    gh.checks = lambda n: (polls.append(1), len(polls) == 3 and gh.runs.update(b16=[901]), real_checks(n))[2]
    assert proc.process(15) == "MERGED" and {("cancel", 900), ("cancel", 901)} <= set(gh.calls) and not {("cancel", 950), ("cancel", 980)} & set(gh.calls)
    proc, q, log, _ = _proc(mq, d, "f2", gh, [16, 17, 18])
    assert [proc.pick("code") for _ in range(4)] == [18, 16, 17, None], "urgent first, then queue order; one a worker holds is not handed out again"

    # the three required full checks: light-only green is relabelled and stays a HOLD; a missing check or an empty list is a HOLD; pending is waited for; gh failing now and then is asked again; CI past the timeout is a HOLD
    gh = FakeGh(); gh.add(20, rounds=[dict(pending=0, checks=green(light=True)), dict(pending=0, checks=green())])
    real_label = gh.label
    gh.label = lambda n, name, add: (real_label(n, name, add), add or gh.prs[n].update(round=1))[0]
    result, ev, _, _ = once(mq, d, "g1", gh, 20)
    assert result == "MERGED" and ev.index("REQUIRED-NOT-GREEN") < ev.index("MERGED")
    for name, checks, why in (("light", green(light=True), "REQUIRED-NOT-GREEN"), ("no secret scan", green()[:2], "secret scan: not listed"), ("none", [], "no check at all")):
        gh = FakeGh(); gh.add(21, rounds=[dict(pending=0, checks=checks)])
        result, ev, log, _ = once(mq, d, "g-" + name, gh, 21)
        assert result == "HOLD" and why in " ".join(log.lines) and not merged(gh), name
    gh = FakeGh(); gh.add(22, rounds=[dict(pending=5, checks=green())])
    assert once(mq, d, "g5", gh, 22)[3].count(30) >= 3 and merged(gh)
    gh = FakeGh(); gh.add(23)
    real_checks, polls = gh.checks, []
    gh.checks = lambda n: (polls.append(1), None if len(polls) in (2, 3, 5) else real_checks(n))[1]
    assert once(mq, d, "g6", gh, 23)[0] == "MERGED" and len(polls) > 5
    gh = FakeGh(); gh.add(24, rounds=[dict(pending=10 ** 6, checks=green())])
    result, ev, log, slept = once(mq, d, "g7", gh, 24, ci_timeout=120)
    assert result == "HOLD" and "pending after 2 minutes" in log.lines[-1] and sum(slept) < 400

    # a refused merge: growing pause, own counter, gh's message in the log; five refusals are a HOLD
    gh = FakeGh(); gh.add(30, merge_results=((False, "required status check expected"), (False, "again"), (False, "again"), (True, "")))
    result, ev, log, slept = once(mq, d, "m1", gh, 30)
    assert result == "MERGED" and ev.count("RETRY-MERGE") == 3 and slept[-3:] == [20, 40, 80] and "mergeState=CLEAN" in [l for l in log.lines if "RETRY-MERGE" in l][0]
    gh = FakeGh(); gh.add(31, merge_results=((False, "no"),) * 9)
    result, ev, log, _ = once(mq, d, "m2", gh, 31)
    assert result == "HOLD" and ev.count("RETRY-MERGE") == 5 and "refused 5 times" in log.lines[-1]

    # main moved while the CI ran: update and run again, merge pinned to the head that contains main; main that never stops: HOLD; CI of the new head not seen: not counted, then HOLD
    gh = FakeGh(); gh.add(32)
    real_checks, polls = gh.checks, []
    gh.checks = lambda n: (polls.append(1), len(polls) == 2 and gh.prs[32].update(behind=1), real_checks(n))[2]
    result, ev, _, _ = once(mq, d, "m3", gh, 32)
    assert result == "MERGED" and "BEHIND-AT-MERGE" in ev and ev.count("UPDATE") == 1 and merged(gh)[0][3].endswith("ee")
    gh = FakeGh(); gh.add(33)
    values = iter([0] + [1] * 80)
    gh.behind_by = lambda n, head: next(values)
    result, ev, log, _ = once(mq, d, "m4", gh, 33)
    assert result == "HOLD" and "main moved" in log.lines[-1] and not merged(gh)
    gh = FakeGh(); gh.add(34, merge="BEHIND", behind=2)["ci_late"] = 3
    result, ev, _, slept = once(mq, d, "m5", gh, 34)
    assert result == "MERGED" and slept.count(15) >= 3 and "NEW-CI-NOT-SEEN" not in ev
    gh = FakeGh(); gh.add(35, merge="BEHIND", behind=2)["ci_late"] = 10 ** 6
    values = iter([2] + [1] * 80)
    gh.behind_by = lambda n, head: next(values)
    result, ev, log, _ = once(mq, d, "m6", gh, 35)
    assert result == "HOLD" and "MOVED-NOT-COUNTED" in ev and "never started" in log.lines[-1]

    # under the merge lock: checks no longer green -> run again; compare unknown -> HOLD; closed meanwhile -> HOLD
    gh = FakeGh(); gh.add(36)
    real_checks, polls = gh.checks, []
    gh.checks = lambda n: (polls.append(1), [dict(c, bucket="pending") for c in green()] if len(polls) == 3 else real_checks(n))[1]
    result, ev, _, _ = once(mq, d, "m7", gh, 36)
    assert result == "MERGED" and ev.index("CHECKS-CHANGED") < ev.index("MERGED")
    gh = FakeGh(); gh.add(37)
    looks = []
    gh.behind_by = lambda n, head: (looks.append(1), None if len(looks) >= 2 else 0)[1]
    result, ev, log, _ = once(mq, d, "m8", gh, 37)
    assert result == "HOLD" and "whether the head contains main" in log.lines[-1] and not merged(gh)
    gh = FakeGh(); gh.add(38)
    real_view, looks = gh.view, []
    gh.view = lambda n: (looks.append(1), dict(real_view(n), state="CLOSED") if len(looks) >= 2 else real_view(n))[1]
    result, ev, log, _ = once(mq, d, "m9", gh, 38)
    assert result == "HOLD" and "changed state" in log.lines[-1] and not merged(gh)

    # a light check the label's run cancelled is no verdict next to its full twin; a failing full check still is; red with main moved on: update, no rerun on the old head
    both = green() + [dict(c, name=c["name"] + " (light)", bucket="cancel") for c in green()[:2]]
    gh = FakeGh(); gh.add(40, rounds=[dict(pending=0, checks=both)])
    assert "RERUN-FAILED" not in once(mq, d, "s1", gh, 40)[1] and merged(gh)
    gh = FakeGh(); gh.add(41, rounds=[dict(pending=0, checks=[dict(c, bucket="fail") if c["name"].startswith("backend") else c for c in both]), dict(pending=0, checks=both)])
    assert once(mq, d, "s2", gh, 41)[1].count("RERUN-FAILED") == 1
    gh = FakeGh(); gh.add(42, rounds=[dict(pending=0, checks=red(5)), dict(pending=0, checks=green(6))])
    real_update, real_checks = gh.update_branch, gh.checks
    gh.update_branch = lambda n: (gh.prs[n].update(round=1, pend=None), real_update(n))[1]
    gh.checks = lambda n: (real_checks(n), gh.prs[n]["round"] == 0 and gh.prs[n].update(behind=3))[0]
    result, ev, _, _ = once(mq, d, "s3", gh, 42)
    assert result == "MERGED" and "BEHIND-AFTER-FAIL" in ev and ("rerun", "5") not in gh.calls

    # a pull request that changes .github/workflows, or whose files gh cannot list, is updated over ssh (gh update-branch exits 0 without pushing there)
    for name, files, ssh in (("plain", ["backend/a.java"], False), ("workflow", [".github/workflows/ci.yml"], True), ("unknown", None, True)):
        gh = FakeGh(); gh.add(43, behind=3)["files"] = files
        result, ev, _, _ = once(mq, d, "wf-" + name, gh, 43, updater=lambda repo, branch, wt, gh=gh: (gh.prs[43].update(merge="CLEAN", behind=0), "ok")[1])
        assert result == "MERGED" and (("update-branch", 43) in gh.calls) != ssh and ("SSH-UPDATE" in ev) == ssh, name

    # only the light run listed right after the label, or nothing registered yet: waited for, not judged
    gh = FakeGh(); gh.add(44)
    real_checks, polls = gh.checks, []
    gh.checks = lambda n: (polls.append(1), green(light=True) if len(polls) <= 3 else real_checks(n))[1]
    result, ev, _, slept = once(mq, d, "s4", gh, 44)
    assert result == "MERGED" and slept.count(15) >= 3 and "REQUIRED-NOT-GREEN" not in ev
    gh = FakeGh(); gh.add(45)
    real_checks, polls = gh.checks, []
    gh.checks = lambda n: (polls.append(1), len(polls) == 23 and gh.prs[45].update(behind=1), [] if len(polls) <= 22 else real_checks(n))[2]
    assert "BEHIND-AT-MERGE" in once(mq, d, "s5", gh, 45)[1]

    # behind only by docs commits: up to date (merged as it is); by a test-read doc, code, an unknown or empty list: synced; GitHub's strict BEHIND: synced, never a refusal
    for name, files, synced in (("docs", ["docs/a.md"], False), ("guarded", ["docs/a.md", "docs/guarded/t.yaml"], True), ("code", ["backend/pom.xml"], True),
                                ("unknown", None, True), ("empty", [], True)):
        gh = FakeGh(); gh.add(46, behind=3)["behind_files"] = files
        assert once(mq, d, "bd-" + name, gh, 46)[0] == "MERGED" and (("update-branch", 46) in gh.calls) == synced and (synced or gh.merged_behind == [3]), name
    gh = FakeGh(); gh.add(47, merge="BEHIND", behind=2)["behind_files"] = ["docs/a.md"]
    assert once(mq, d, "bd-strict", gh, 47)[0] == "MERGED" and ("update-branch", 47) in gh.calls
    gh = FakeGh(); gh.add(48, merge_results=((False, "not up to date"),) * 2)["behind_files"] = ["docs/a.md"]
    real_merge = gh.merge
    gh.merge = lambda n, head: (lambda out: (out[0] or gh.prs[n].update(merge="BEHIND", behind=1), out)[1])(real_merge(n, head))
    result, ev, _, _ = once(mq, d, "bd-refused", gh, 48)
    assert result == "MERGED" and ev.count("UPDATE") == 2 and ev.count("RETRY-MERGE") == 2
    gh = FakeGh(); gh.add(49, merge_results=((False, "behind"),) * 20)["behind_files"] = ["docs/a.md"]
    real_merge = gh.merge
    gh.merge = lambda n, head: (lambda out: (gh.prs[n].update(merge="BEHIND", behind=1), out)[1])(real_merge(n, head))
    result, ev, log, _ = once(mq, d, "bd-moving", gh, 49)
    assert result == "HOLD" and "main moved" in log.lines[-1] and "refused" not in log.lines[-1]

    # the docs lane never sleeps for the code lane; two ssh updates never share the scratch worktree
    gh = FakeGh(); gh.add(50)
    assert 30 not in once(mq, d, "s6", gh, 50, lane=lambda g, n: "docs")[3]
    inside = {"n": 0, "max": 0}

    def updater(repo, branch, wt):
        inside["n"] += 1
        inside["max"] = max(inside["max"], inside["n"])
        time.sleep(0.05)
        inside["n"] -= 1
        return "ok"
    gh = FakeGh(); gh.add(51, merge="BEHIND", update_ok=False); gh.add(52, merge="BEHIND", update_ok=False)
    proc, q, log, _ = _proc(mq, d, "s7", gh, [51, 52], updater=updater)
    threads = [threading.Thread(target=proc.update, args=(n, f"b{n}", 0)) for n in (51, 52)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    assert inside["max"] == 1


def _release(mq, d):
    # no passing review: HOLD before any CI is spent (no update, no label); the newest verdict 必改; gh cannot list reviews
    for name, change in (("none", lambda p: p.update(reviews=[])), ("必改", lambda p: p["reviews"].append(review(p["head"], "必改", at="2026-10-29T05:00:00Z"))),
                         ("unreadable", lambda p: p.update(reviews=None))):
        gh = FakeGh(); change(gh.add(60, merge="BEHIND", behind=1))
        result, ev, log, _ = once(mq, d, "r-" + name, gh, 60)
        assert result == "HOLD" and "not released" in log.lines[-1] and not [c for c in gh.calls if c[0] in ("merge", "update-branch", "label")], (name, gh.calls)
    # lead's own pull request (branch claude/one-lead-...): an approval comment does not release it, a first-round 通过 does (#856); another branch: the approval does
    for name, branch, reviews, released in (("lead, approval only", "claude/one-lead-860-x", [], "HOLD"), ("lead, first round", "claude/one-lead-860-x", None, "MERGED"),
                                            ("other session, approval only", "claude/one-platform-860-x", [], "MERGED"),
                                            ("old lead name, approval only", "claude/one-leader-860-x", [], "HOLD"),
                                            ("unnamed branch, approval only", "fix-something", [], "HOLD"),
                                            ("no branch, approval only", "", [], "HOLD")):
        gh = FakeGh(); p = gh.add(66)
        p["branch"] = branch
        if reviews is not None:
            p["reviews"] = reviews
        p["comments"] = [approval(p["head"])]
        result, ev, log, _ = once(mq, d, "r-lead-" + name.replace(" ", "-").replace(",", ""), gh, 66)
        assert result == released, (name, result, log.lines[-1:])
        if released == "HOLD":
            assert "approvals do not count on lead's own pull request" in log.lines[-1], log.lines[-1]
    # carried across the queue's merge of main: an approval of the pre-merge commit releases a known non-lead branch, never lead's own (#856)
    for name, branch, ok in (("other session", "claude/one-platform-67-x", True), ("lead", "claude/one-lead-67-x", False), ("old name", "claude/one-leader-67-x", False)):
        gh = FakeGh(); p = gh.add(67)
        merged_head, before = p["head"], "d" * 40
        p.update(branch=branch, reviews=[], comments=[approval(before)])
        p["merges"][merged_head] = before
        p["pids"].update({merged_head: "1" * 40, before: "2" * 40})
        proc = _proc(mq, d, "r-carry-appr-" + name.replace(" ", "-"), gh)[0]
        released, why = proc.released(67, merged_head)
        assert released is ok, (name, why)
        assert not ok or "carried across the merge of main" in why, why
    gh = FakeGh(); p = gh.add(61, rounds=[dict(pending=2, checks=green())])
    result, ev, log, _ = once(mq, d, "r-late", gh, 61, on_sleep=lambda s: p["reviews"].append(review(p["head"], "必改", at="2026-10-29T05:00:00Z")))
    assert result == "HOLD" and "LABEL-QUEUED" in ev and not merged(gh) and "必改" in log.lines[-1]
    # the queue's own update changed the head: the review of the old head speaks for it by patch-id, and when merging main changed the context (#760, 2026-10-29) by the merge it carries over
    gh = FakeGh(); p = gh.add(62, merge="BEHIND", behind=1)
    assert once(mq, d, "r-pid", gh, 62)[0] == "MERGED" and ("patch-id", 62, p["head"]) in gh.calls
    gh = FakeGh(); p = gh.add(63, merge="BEHIND", behind=1); p["pids"][f"{63:040x}"[:-2] + "ee"] = "6" * 40
    result, ev, log, _ = once(mq, d, "r-carry", gh, 63)
    assert result == "MERGED", log.lines
    # not carried: a commit the author added after the release (no merge of main), a merge that is not only main (git says no), a 必改 on the merged head itself
    for name, change in (("author commit", lambda p: p["merges"].clear()), ("必改 on the merge", lambda p: p["reviews"].append(review(p["head"], "必改", at="2026-10-29T05:00:00Z")))):
        gh = FakeGh(); p = gh.add(64, merge="BEHIND", behind=1); p["pids"][f"{64:040x}"[:-2] + "ee"] = "6" * 40
        real_update = gh.update_branch
        gh.update_branch = lambda n, p=p, change=change, real=real_update: (real(n), change(p))[0]
        result, ev, log, _ = once(mq, d, "r-nocarry-" + name, gh, 64)
        assert result == "HOLD" and not merged(gh) and "not released" in log.lines[-1], name
    # the coordinator's approval of a fix after a 必改; the docs lane needs no review
    gh = FakeGh(); p = gh.add(64); p["reviews"], p["pids"]["c" * 40] = [review("c" * 40, "必改")], "7" * 40; p["comments"] = [approval(p["head"][:9])]
    p["branch"] = "claude/one-platform-64-x"   # approvals count only on a branch of a known session other than lead (#856)
    assert once(mq, d, "r-appr", gh, 64)[0] == "MERGED"
    for name, files, outcome in (("plain", ["docs/a.md"], "MERGED"), ("guarded", ["docs/a.md", "scripts/merge/x.md"], "HOLD"), ("unknown", None, "HOLD")):
        gh = FakeGh(); p = gh.add(65); p["reviews"], p["files"] = [], files
        assert once(mq, d, "r-docs-" + name, gh, 65, lane=lambda g, n: "docs")[0] == outcome, "the docs lane needs no review, unless it touches a guarded path or gh cannot list its files"

    # read-back: MERGED with the pinned head as second parent -> the main checkout is fast-forwarded; anything else -> ALERT, both lanes paused, no fast-forward
    for name, seen, good in (("ok", None, True), ("parent", ("MERGED", ["m" * 40, "f" * 40]), False), ("state", ("CLOSED", ["m" * 40, f"{66:040x}"]), False), ("gh", None, False)):
        gh = FakeGh(); p = gh.add(66)
        if name != "ok":
            p["readback"] = seen
        proc, q, log, slept = _proc(mq, d, "rb-" + name, gh, [66])
        called = []
        proc.ff_main = lambda n, since: called.append(n)
        assert proc.process(66) == "MERGED" and bool(called) == good and proc.pause.is_paused() != good, (name, events(log))
        assert good or ("READBACK" in log.lines[-1] and slept.count(5) == 2), log.lines
    gh = FakeGh(); gh.add(67, merge_results=[(False, "no")] * 6)
    proc, q, log, _ = _proc(mq, d, "rb-refused", gh, [67])
    proc.ff_main = lambda n, since: (_ for _ in ()).throw(AssertionError("a refused merge does not touch the main checkout"))
    assert proc.process(67) == "HOLD"


def _pause(mq, d):
    gh = FakeGh(); gh.add(70)
    proc, q, log, _ = _proc(mq, d, "p1", gh, [70])
    pause = proc.pause
    gh.main_runs.update(push={"id": 9001, "status": "in_progress", "conclusion": None}, schedule={"id": 9002, "status": "completed", "conclusion": "cancelled"})
    assert pause.check() is False and not pause.is_paused(), "a run still going or cancelled is not red"
    gh.main_runs["push"] = {"id": 9001, "status": "completed", "conclusion": "failure"}
    assert pause.check() is True and pause.check() is True and sum(" PAUSED " in l and "push run 9001" in l for l in log.lines) == 1, "paused, logged once"
    stops = iter([False, False, False, True])
    mq.run_lane(proc, "code", stop=lambda: next(stops))
    assert q.numbers() == [70] and not gh.calls, "a paused lane takes nothing"
    assert pause.resume() == [("push", "9001")] and pause.check() is False, "the acknowledged run does not pause again"
    gh.main_runs["schedule"] = {"id": 9003, "status": "completed", "conclusion": "failure"}
    assert pause.check() is True and "nightly run 9003" in open(pause.paused_file).read(), "a new red run does"
    gh.main_runs["push"] = {"id": 9004, "status": "completed", "conclusion": "failure"}
    for named in (["9003"], []):
        with contextlib.suppress(SystemExit):
            pause.resume(named)
            raise AssertionError("resume must name exactly the red runs")
    assert pause.is_paused() and sorted(r for _, r in pause.resume(["9003", "9004"])) == ["9003", "9004"] and not pause.is_paused()
    # gh cannot say: the lanes wait without a pause file, one line (also on a machine just started)
    gh.main_runs["push"] = mq.SignalError("rate limited")
    pause.clock = lambda: 5.0
    assert pause.check() is True and pause.check() is True and not pause.is_paused() and sum(" WAIT-SIGNAL " in l for l in log.lines) == 1
    # main turns red after the checks went green: the merge waits under the lock until resume
    gh = FakeGh(); gh.add(71)
    proc, q, log, _ = _proc(mq, d, "p2", gh, [71], on_sleep=lambda s: s == 60 and proc.pause.resume())
    real_checks, polls = gh.checks, []
    gh.checks = lambda n: (polls.append(1), len(polls) == 2 and gh.main_runs.update(push={"id": 9100, "status": "completed", "conclusion": "failure"}), real_checks(n))[2]
    assert proc.process(71) == "MERGED" and events(log).index("WAIT-PAUSED") < events(log).index("RESUMED") < events(log).index("MERGED")
    # one runner per queue
    os.makedirs(os.path.join(d, "p3"))
    with mq.runner_lock(os.path.join(d, "p3")):
        with contextlib.suppress(SystemExit):
            with mq.runner_lock(os.path.join(d, "p3")):
                raise AssertionError("a second runner must be refused")
    with mq.runner_lock(os.path.join(d, "p3")):
        pass


def _runner(mq, d):
    # a failing step: ERROR, growing pause, retried; the third in a row is a HOLD and the next pull request is not stuck; a lane survives a failing pause check and a failing removal
    gh = FakeGh(); gh.add(80); gh.add(81)
    proc, q, log, slept = _proc(mq, d, "x1", gh, [80, 81])
    real, boom = proc.process, []
    proc.process = lambda n: real(n) if n != 80 or len(boom) >= 2 else (boom.append(n), (_ for _ in ()).throw(SystemExit("gh pr view failed (1)") if len(boom) == 1 else RuntimeError("socket")))
    stops = iter([False] * 8 + [True] * 100)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert events(log).count("ERROR") == 2 and [s for s in slept if s in (60, 120)] == [60, 120] and q.numbers() == [] and any(l.split()[-1] == "MERGED" for l in log.lines if " DEQUEUE " in l)
    gh = FakeGh(); gh.add(82); gh.add(83)
    proc, q, log, _ = _proc(mq, d, "x2", gh, [82, 83])
    real = proc.process
    proc.process = lambda n: real(n) if n != 82 else (_ for _ in ()).throw(RuntimeError("always"))
    stops = iter([False] * 8 + [True] * 100)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert events(log).count("ERROR") == 3 and "HOLD" in events(log) and q.numbers() == [] and [c[1] for c in merged(gh)] == [83]
    gh = FakeGh(); gh.add(84)
    proc, q, log, _ = _proc(mq, d, "x3", gh, [84])
    real_check, failed = proc.pause.check, []
    proc.pause.check = lambda: real_check() if failed else (failed.append(1), (_ for _ in ()).throw(OSError("disk full")))[1]
    q.remove = lambda *a: (_ for _ in ()).throw(OSError("read-only"))
    stops = iter([False] * 6 + [True] * 100)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert "disk full" in " ".join(log.lines) and "MERGED" in events(log)

    # two lanes at once: merges serialized, the second sees main moved and updates; never merged behind main; log lines whole
    gh = FakeGh(); gh.add(85); gh.add(86); gh.slow = True
    proc, q, log, _ = _proc(mq, d, "x4", gh, [85, 86], lane=lambda g, n: {85: "code", 86: "docs"}[n])
    mq.run_loop(proc, idle=0, stop=lambda: not q.numbers() or len(log.lines) > 400)
    assert sorted(c[1] for c in merged(gh)) == [85, 86] and gh.merged_behind == [0, 0] and all(LOG_LINE.match(l) for l in log.lines)
    # the fast lane: a docs pull request queued behind a code one whose heavy run is still going merges first, without waiting for it
    gh = FakeGh(); gh.add(91, rounds=[dict(pending=40, checks=green())]); gh.add(92)
    proc, q, log, _ = _proc(mq, d, "x6", gh, [91, 92], lane=lambda g, n: {91: "code", 92: "docs"}[n], on_sleep=lambda s: time.sleep(0.005))
    mq.run_loop(proc, idle=0, stop=lambda: not q.numbers() or len(log.lines) > 400)
    assert [c[1] for c in merged(gh)] == [92, 91], merged(gh)
    # across lanes with dependencies: 88 (code) queued first waits for 87 (docs)
    gh = FakeGh(); gh.add(87); gh.add(88)
    proc, q, log, _ = _proc(mq, d, "x5", gh, [], lane=lambda g, n: {87: "docs", 88: "code"}[n])
    q.enqueue(88, after=[87]); q.enqueue(87)
    mq.run_loop(proc, idle=0, stop=lambda: not q.numbers() or len(log.lines) > 600)
    assert [c[1] for c in merged(gh)] == [87, 88] and all("lane=" in l for l in log.lines if "pr=#8" in l)


def _queue(mq, d):
    # the queue file under concurrent processes: no enqueue lost to a remove, no temporary file left, also after a failed write
    qpath = os.path.join(d, "lock", "merge.queue")
    q = mq.Queue(qpath)
    q.enqueue(*range(100, 110))
    me = os.path.abspath(mq.__file__)
    stub = "import sys; sys.path.insert(0, sys.argv[1]); import mq; mq.mq_title.enforce = lambda *a: None; mq.main(sys.argv[2:])"   # the title check has its own case (mq_title_selftest); this one is about the queue file, not gh
    procs = [subprocess.Popen([sys.executable, "-c", stub, os.path.dirname(me), "--queue", qpath, op, str(n)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) for op, ns in (("enqueue", range(1, 11)), ("remove", range(100, 110))) for n in ns]
    assert all(p.wait() == 0 for p in procs) and sorted(q.numbers()) == list(range(1, 11))
    real_replace = os.replace
    os.replace = lambda *a: (_ for _ in ()).throw(OSError("disk full"))
    try:
        with contextlib.suppress(OSError):
            q.enqueue(555)
            raise AssertionError("a failed write must raise")
    finally:
        os.replace = real_replace
    assert 555 not in q.numbers() and not [f for f in os.listdir(os.path.dirname(qpath)) if f.startswith(".queue-")]
    q.remove(*range(1, 11)); q.enqueue(1, 2, 3, 4)
    assert q.enqueue(5, 6) == [5, 6] and q.enqueue(5) == [] and q.numbers().count(5) == 1
    q.before(4, 1); q.before(99, 7777)
    open(qpath, "a").write("# note\n")
    q.remove(99); q.enqueue(98)
    assert q.numbers() == [4, 1, 2, 3, 5, 6, 98] and "# note" in open(qpath).read()

    # dependencies: kept through before(), self-dependency and cycles refused and change nothing
    q = mq.Queue(os.path.join(d, "dep", "merge.queue"))
    assert q.enqueue(71, after=[70]) == [71] and q.enqueue(70, 72) == [70, 72] and q.enqueue(71, after=[72]) == []
    q.before(71, 72)
    assert open(q.path).read() == "70\n71 after=70,72\n72\n"
    for bad in ((70, [70]), (70, [71]), (72, [71])):
        with contextlib.suppress(ValueError):
            q.enqueue(bad[0], after=bad[1])
            raise AssertionError(f"cycle accepted: {bad}")
    assert q.deps(70) == set() and q.deps(72) == set()
    # pick: a waiting pull request is skipped (logged once, again after wait_repeat), merged dependency releases it; gh unsure: not released; closed dependency: HOLD and out
    gh = FakeGh()
    for n in (70, 71, 72, 73, 74):
        gh.add(n)
    proc, q, log, _ = _proc(mq, d, "dep2", gh, [], lane=lambda g, n: "docs" if n == 70 else "code")
    q.enqueue(71, after=[70]); q.enqueue(72, 70)
    assert proc.pick("code") == 72 and proc.pick("code") is None and proc.pick("code") is None
    waits = [l for l in log.lines if " WAIT-DEP " in l]
    assert len(waits) == 1 and "pr=#71" in waits[0] and "waits for #70" in waits[0]
    proc.wait_repeat = 0
    assert proc.pick("code") is None and len([l for l in log.lines if " WAIT-DEP " in l]) == 2
    real_view = gh.view
    gh.view = lambda n: None if n == 70 else real_view(n)
    assert proc.pick("code") is None and "gh could not tell the state of #70" in log.lines[-1]
    gh.view = real_view
    gh.prs[70]["state"] = "MERGED"
    assert proc.pick("code") == 71
    gh.prs[73]["state"] = "CLOSED"
    q.enqueue(74, after=[73])
    assert proc.pick("code") is None and 74 not in q.numbers() and "dependency #73 was closed without merge" in " ".join(log.lines)
    # `once` learns the lane; a line without a pull request has none
    gh = FakeGh(); gh.add(90)
    result, ev, log, _ = once(mq, d, "dep3", gh, 90, lane=lambda g, n: "docs")
    log.event("ERROR", None, None, None, "x")
    assert all("lane=docs" in l for l in log.lines[:-1]) and "lane=" not in log.lines[-1]

    # the command line: values checked, all or nothing, unreadable lines named
    qpath, lpath = os.path.join(d, "cli", "merge.queue"), os.path.join(d, "cli", "merge.log")

    def cli(*args):
        err = io.StringIO()
        with contextlib.redirect_stderr(err), contextlib.redirect_stdout(io.StringIO()) as out, mock.patch.object(mq.mq_title, "enforce", lambda *a: None):   # the title check has its own case (mq_title_selftest)
            try:
                mq.main(["--queue", qpath, "--log", lpath, *args])
                code = 0
            except SystemExit as e:
                code, _ = (e.code if isinstance(e.code, int) else 1), err.write(str(e.code))
        return code, out.getvalue(), err.getvalue()
    assert cli("enqueue", "449", "--after", "448")[0] == 0 and open(qpath).read() == "449 after=448\n"
    for bad in (["--after=-5"], ["--after", "0"], ["--after", "x"]):
        assert cli("enqueue", "500", *bad)[0] != 0 and "500" not in open(qpath).read(), bad
    code, _, err = cli("enqueue", "452", "451", "--after", "451")
    assert code != 0 and "nothing queued" in err and open(qpath).read() == "449 after=448\n"
    code, _, err = cli("enqueue", "449", "--after", "447")
    assert code == 0 and "already queued" in err and open(qpath).read() == "449 after=447,448\n"
    open(qpath, "a").write("600 after=#448\n")
    code, out, err = cli("list")
    assert out.split("\n")[0] == "449 after=447,448" and "not understood" in err


def _remove_busy(mq, d):
    """`remove` of a pull request a worker is processing: refused, or the worker stops before the merge; never a silent carry-on (#574 was taken out and merged anyway, 2026-10-22)."""
    qpath = os.path.join(d, "busy", "merge.queue")
    q = mq.Queue(qpath)
    q.enqueue(300, 301)

    def cli(*args):
        try:
            mq.main(["--queue", qpath, "--log", qpath + ".log", *args])
        except SystemExit as error:
            return str(error)
    q.mark_working(300)
    assert "cannot be taken back" in cli("remove", "300") and cli("remove", "301", "300") and q.numbers() == [300, 301], "refused, and a refused call removes nothing"
    assert cli("remove", "301") is None and cli("remove", "--cancel", "300") is None and q.numbers() == [] and q.cancel_requested(300)
    q.unmark_working(300)
    assert not q.cancel_requested(300) and q.working() == {}
    open(qpath + ".working", "w").write("300 999999\n")
    assert q.working() == {}, "a marker whose runner is gone is stale"
    # claim and removal are one step; a request nobody processes is not kept; a new round inherits no old request; the runner's start clears what a killed runner left
    q.enqueue(320, 321)
    assert q.claim(320) and q.remove_checked({320, 321}, False) == {320: os.getpid()} and q.remove_checked({321}, False) == {} and q.numbers() == [320]
    assert q.remove_checked({320}, True) and not q.claim(320) and not q.claim(321)
    q.unmark_working(320)
    q.request_cancel(330)
    assert not q.cancel_requested(330)
    open(qpath + ".cancel", "w").write("332\n")
    q.enqueue(332)
    assert q.claim(332) and not q.cancel_requested(332)
    open(qpath + ".working", "w").write("331 1\n"); open(qpath + ".cancel", "w").write("331\n")
    q.reset_working()
    assert q.working() == {} and not q.cancel_requested(331)

    # the worker stops at a checkpoint: before the update or label, in the CI wait, under the merge lock; too late once gh was asked to merge
    def cancel_at(name, n, merge="CLEAN", hook=None, on_sleep=None):
        gh = FakeGh(); gh.add(n, merge=merge, rounds=[dict(pending=3, checks=green())])
        holder = {}
        proc, q2, log, _ = _proc(mq, d, name, gh, [n], on_sleep=on_sleep and (lambda s: on_sleep(s) and holder["q"].request_cancel(n)))
        holder["q"] = q2
        if hook:
            hook(gh, q2)
        return proc.process(n), gh, q2, log
    result, gh, q2, log = cancel_at("cb1", 310, merge="BEHIND", hook=lambda gh, q2: setattr(gh, "view", (lambda real: lambda n: (q2.request_cancel(n), real(n))[1])(gh.view)))
    assert result == "CANCELLED" and not [c for c in gh.calls if c[0] in ("update-branch", "merge") or c[:4] == ("label", 310, "queued", True)]
    result, gh, q2, log = cancel_at("cb2", 311, on_sleep=lambda s: s == 30)
    assert result == "CANCELLED" and not merged(gh) and q2.working() == {} and not q2.cancel_requested(311) and ("label", 311, "queued", False) in gh.calls
    looks = []
    result, gh, q2, log = cancel_at("cb3", 312, hook=lambda gh, q2: setattr(gh, "behind_by", lambda n, h: (looks.append(1), len(looks) == 2 and q2.request_cancel(n), 0)[2]))
    assert result == "CANCELLED" and not merged(gh), "the last checkpoint under the merge lock"
    gh = FakeGh(); gh.add(313)
    proc, q2, log, _ = _proc(mq, d, "cb4", gh, [313])
    real_merge = gh.merge
    gh.merge = lambda n, h: (q2.request_cancel(n), q2.remove(n), q2.enqueue(n), real_merge(n, h))[3]   # remove --cancel and queued again while the merge call is under way
    stops = iter([False, True] + [True] * 20)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert "MERGED" in events(log) and "CANCELLED" not in events(log) and q2.numbers() == [313], "too late: merged, and the entry queued again stays"
    # in the lane: taken out between look and claim -> not started; CANCELLED keeps an entry queued again; a too-late request keeps it as well
    gh = FakeGh(); gh.add(314)
    proc, q3, log, _ = _proc(mq, d, "cb5", gh, [314])
    real_claim = q3.claim
    q3.claim = lambda n: (q3.remove(n), real_claim(n))[1]
    stops = iter([False, True] + [True] * 20)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert "SKIPPED" in events(log) and "START" not in events(log) and not proc.busy
    gh = FakeGh(); gh.add(315, rounds=[dict(pending=3, checks=green())])
    holder = {}
    proc, q4, log, _ = _proc(mq, d, "cb6", gh, [315], on_sleep=lambda s: s == 30 and holder["q"].request_cancel(315))
    holder["q"] = q4
    real = proc.process
    proc.process = lambda n: (real(n), q4.enqueue(n))[0]
    stops = iter([False, True] + [True] * 20)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))
    assert "CANCELLED" in events(log) and q4.numbers() == [315]
    # a claim that fails leaves nothing busy; release frees busy even when the mark cannot be removed; `once` marks the pull request while it works
    gh = FakeGh(); gh.add(316)
    proc, q5, log, _ = _proc(mq, d, "cb7", gh, [316])
    q5.claim = lambda n: (_ for _ in ()).throw(OSError("disk full"))
    with contextlib.suppress(OSError):
        proc.pick("code")
    assert proc.busy == set() and q5.working() == {}
    q5.unmark_working = lambda n: (_ for _ in ()).throw(OSError("disk full"))
    proc.busy.add(316)
    with contextlib.suppress(OSError):
        proc.release(316)
    assert 316 not in proc.busy
    gh = FakeGh(); gh.add(317, rounds=[dict(pending=2, checks=green())])
    seen, holder = [], {}
    proc, q6, log, _ = _proc(mq, d, "cb8", gh, [], on_sleep=lambda s: seen.append(dict(holder["q"].working())))
    holder["q"] = q6
    assert proc.process(317) == "MERGED" and any(317 in w for w in seen) and q6.working() == {}


def _head_changed(mq, d):
    """GOV-1022-MRG-06: a head that is not the one approved at enqueue is never merged; out of the queue with HEAD-CHANGED (both shas, owner)."""
    gh = FakeGh(); gh.add(1)
    proc, q, log, _ = _proc(mq, d, "h1", gh, [1])
    q.set_head(1, f"{1:040x}")
    assert proc.process(1) == "MERGED" and "HEAD-CHANGED" not in events(log)
    gh = FakeGh(); gh.add(2, merge="BEHIND", behind=1)["head"] = "f" * 40
    proc, q, log, _ = _proc(mq, d, "h2", gh, [2])
    q.set_head(2, f"{2:040x}")
    mq.run_lane(proc, "code", stop=lambda: not q.numbers())
    line = [l for l in log.lines if " HEAD-CHANGED " in l][0]
    assert f"approved {2:040x}"[:18] in line and "now fffffffff" in line and q.head_of(2) is None and q.numbers() == []
    assert not [c for c in gh.calls if c[0] in ("merge", "update-branch") or (c[0] == "label" and c[3])]
    gh = FakeGh(); gh.add(4, rounds=[dict(pending=2, checks=green())])
    proc, q, log, _ = _proc(mq, d, "h4", gh, [4], on_sleep=lambda s: gh.prs[4].update(head="d" * 40))
    q.set_head(4, f"{4:040x}")
    assert proc.process(4) == "HEAD-CHANGED" and not merged(gh), "pushed while the CI ran"
    gh = FakeGh(); gh.add(5, merge="BEHIND", behind=3)
    proc, q, log, _ = _proc(mq, d, "h5", gh, [5])
    q.set_head(5, f"{5:040x}")
    assert proc.process(5) == "MERGED" and "HEAD-CHANGED" not in events(log), "the queue's own update is not the author's push"
    assert q.head_of(5) == gh.prs[5]["head"] and _proc(mq, d, "h5", gh, [5])[0].head_moved(5, gh.view(5)) is None, "kept on disk for a restarted runner"
    q = mq.Queue(os.path.join(d, "h7", "merge.queue")); q.enqueue(7); q.set_head(7, f"{7:040x}")
    with contextlib.redirect_stderr(io.StringIO()):
        mq.record_heads(q, [7])   # the fake gh binary does not answer
    assert q.head_of(7) is None, "an old approval must not outlive a failed read"
    gh = FakeGh(); gh.add(6)["head"] = "c" * 40
    gh.prs[6]["reviews"] = [review("c" * 40)]
    assert once(mq, d, "h6", gh, 6)[0] == "MERGED", "nothing recorded: nothing to compare"
    # enqueue asks gh only for the view (card #736): no comment, review or label call
    probe, real = type("ViewOnly", (), {"view": lambda self, n: {"state": "OPEN", "merge": "CLEAN", "labels": [], "branch": "b", "head": f"{n:040x}", "title": f"fix(q): 修复（#{n}）", "body": f"Closes #{n}\n"}})(), mq.Gh
    mq.Gh, enforce = (lambda: probe), mq.mq_acceptance.enforce
    mq.mq_acceptance.enforce = lambda *args: None   # the guard has its own case (mq_acceptance_selftest); this one must not touch the network
    try:
        mq.main(["--queue", os.path.join(d, "dev", "q"), "enqueue", "21"])
    finally:
        mq.Gh, mq.mq_acceptance.enforce = real, enforce
    assert mq.Queue(os.path.join(d, "dev", "q")).head_of(21) == f"{21:040x}"


def _gh_commands(mq, d):
    """The real Gh class against the fake gh binary: the arguments it builds and how it reads the answers."""
    rows = [{"databaseId": 7, "status": "in_progress", "conclusion": "", "headSha": "s7", "headBranch": "main"}, {"databaseId": 6, "status": "completed", "conclusion": "cancelled", "headSha": "s6", "headBranch": "main"},
            {"databaseId": 5, "status": "completed", "conclusion": "failure", "headSha": "s5", "headBranch": "main"}]
    reviews = [[{"body": "b1", "commit_id": "c1", "submitted_at": "t1"}], [{"body": None, "commit_id": "c2", "submitted_at": None}]]
    fake_gh(d, [(["pr", "view", "1", "--json", "state,mergeStateStatus,labels,headRefName,headRefOid,title,body"], json.dumps({"state": "OPEN", "mergeStateStatus": "CLEAN", "labels": [{"name": "queued"}], "headRefName": "br", "headRefOid": "abc", "title": "t", "body": "b"}), "", 0),
                (["api", "repos/{owner}/{repo}/compare/main...abc"], "3\n", "", 0),
                (["api", "--paginate", "repos/{owner}/{repo}/pulls/9/files"], "docs/a.md\ndocs/old.md\n", "", 0),
                (["api", "repos/{owner}/{repo}/compare/abc...main", "--jq", "[.files[] | .filename, .previous_filename] | map(select(. != null)) | .[]"], "docs/x.md\n", "", 0),
                (["api", "repos/{owner}/{repo}/compare/abc...main", "--jq", ".files | length"], "1\n", "", 0),
                (["api", "repos/{owner}/{repo}/compare/big...main", "--jq", "[.files[] | .filename, .previous_filename] | map(select(. != null)) | .[]"], "docs/x.md\n", "", 0),
                (["api", "repos/{owner}/{repo}/compare/big...main", "--jq", ".files | length"], "300\n", "", 0),
                (["run", "view", "7"], "step one\nstep two\n", "", 0), (["run", "view", "8"], "", "boom", 1),
                (["pr", "checks", "1"], json.dumps([{"name": "x", "bucket": "pass", "link": ""}]), "", 0), (["pr", "checks", "2"], "", "no checks reported\n", 0), (["pr", "checks", "3"], "", "down\n", 1),
                (["pr", "merge", "4"], "", "Pull request is not mergeable\n", 1), (["pr", "merge", "5"], "merged\n", "", 0),
                (["run", "list", "--workflow", "ci", "--event", "push"], json.dumps(rows), "", 0), (["run", "list", "--workflow", "ci", "--event", "schedule"], "not json", "boom", 1),
                (["api", "--paginate", "--slurp", "repos/{owner}/{repo}/pulls/1/reviews"], json.dumps(reviews), "", 0), (["api", "--paginate", "--slurp", "repos/{owner}/{repo}/pulls/2/reviews"], "{}", "", 0),
                (["api", "--paginate", "--slurp", "repos/{owner}/{repo}/pulls/3/reviews"], "[]", "rate", 1),
                (["pr", "view", "1", "--json", "comments"], json.dumps({"comments": [{"author": {"login": "YeliangDu"}, "body": "hi", "createdAt": "t"}, {"author": None, "body": None}]}), "", 0),
                (["pr", "view", "2", "--json", "comments"], "", "down", 1),
                (["pr", "view", "1", "--json", "state,mergeCommit"], json.dumps({"state": "MERGED", "mergeCommit": {"oid": "mc"}}), "", 0),
                (["pr", "view", "2", "--json", "state,mergeCommit"], json.dumps({"state": "OPEN", "mergeCommit": None}), "", 0),
                (["api", "repos/{owner}/{repo}/commits/mc"], "p1 p2\n", "", 0)])
    gh = mq.Gh()
    assert gh.view(1) == {"state": "OPEN", "merge": "CLEAN", "labels": ["queued"], "branch": "br", "head": "abc", "title": "t", "body": "b"} and gh.view(99) is None
    assert gh.behind_by(1, "abc") == 3 and gh.files(9) == ["docs/a.md", "docs/old.md"] and gh.behind_files("abc") == ["docs/x.md"] and gh.behind_files("big") is None, "a cut-off list (300 files) is no answer"
    assert gh.checks(1) == [{"name": "x", "bucket": "pass", "link": ""}] and gh.checks(2) == [] and gh.checks(3) is None, "no check yet is an empty list, a failed call None"
    assert gh.failed_steps(7) == ["step one", "step two"] and gh.failed_steps(8) is None
    assert gh.merge(4, "h4") == (False, "Pull request is not mergeable") and gh.merge(5, "h5") == (True, "merged")
    assert gh.main_run("push")["id"] == 5, "the newest ended, not cancelled run decides"
    with contextlib.suppress(mq.SignalError):
        gh.main_run("schedule")
        raise AssertionError("gh failing must raise SignalError")
    assert gh.reviews(1) == [{"body": "b1", "commit": "c1", "at": "t1"}, {"body": "", "commit": "c2", "at": ""}] and gh.reviews(2) is None and gh.reviews(3) is None
    assert gh.comments(1) == [{"author": "YeliangDu", "body": "hi", "at": "t"}, {"author": None, "body": "", "at": ""}] and gh.comments(2) is None
    assert gh.readback(1) == ("MERGED", ["p1", "p2"]) and gh.readback(2) == ("OPEN", []) and gh.readback(3) is None
    calls = [json.loads(l) for l in open(os.environ["FAKE_GH_LOG"]).read().splitlines()]
    assert ["pr", "merge", "5", "--merge", "--match-head-commit", "h5"] in calls, "the merge is pinned to the head"
    assert ["run", "list", "--workflow", "ci", "--event", "push", "--limit", "20", "--json", "databaseId,status,conclusion,headSha,headBranch", "--branch", "main"] in calls
    fake_gh(d, [])


def _git(mq, d):
    def g(cwd, *a):
        r = subprocess.run(["git", "-c", "commit.gpgsign=false", *a], cwd=cwd, capture_output=True, text=True)
        assert r.returncode == 0, (a, r.stderr)
        return r.stdout.strip()

    def commit(work, path, text, msg):
        open(os.path.join(work, path), "w").write(text)
        g(work, "add", path); g(work, "commit", "-q", "-m", msg)
        return g(work, "rev-parse", "HEAD")
    origin, work = os.path.join(d, "o.git"), os.path.join(d, "w")
    g(d, "init", "-q", "--bare", "-b", "main", origin); g(d, "clone", "-q", origin, work)
    commit(work, "a.txt", "a\n", "base"); g(work, "push", "-q", "origin", "main")

    # patch-id as the reviewer takes it: the queue's merge of main keeps it, a changed change does not, a commit git does not have has none
    g(work, "checkout", "-q", "-b", "pr"); c1 = commit(work, "x.txt", "x\n", "change"); g(work, "push", "-q", "origin", "HEAD:refs/pull/1/head")
    g(work, "checkout", "-q", "main"); commit(work, "m.txt", "m\n", "main moves"); g(work, "push", "-q", "origin", "main")
    g(work, "checkout", "-q", "pr"); g(work, "merge", "-q", "--no-edit", "main"); c2 = g(work, "rev-parse", "HEAD"); g(work, "push", "-q", "origin", "HEAD:refs/pull/1/head")
    c3 = commit(work, "x.txt", "y\n", "another change")
    g(work, "checkout", "-q", "-b", "ws", "main"); c4 = commit(work, "x.txt", "  x\n", "the change, indented")
    other = os.path.join(d, "w2"); g(d, "clone", "-q", origin, other)   # a reviewed head the author rebased away: on GitHub, in no ref, not in this clone
    g(other, "checkout", "-q", "-b", "gone", "origin/main"); c5 = commit(other, "x.txt", "x\n", "the change, rebased away")
    g(other, "push", "-q", "origin", "HEAD:refs/tmp/gone"); g(other, "push", "-q", "origin", ":refs/tmp/gone"); g(origin, "config", "uploadpack.allowAnySHA1InWant", "true")
    mq.Gh.RETRY_PAUSE = 0
    gh = mq.Gh()
    assert gh.patch_id(work, 1, c1) == gh.patch_id(work, 1, c2) == gh.patch_id(work, 1, c5) != gh.patch_id(work, 1, c3), "the queue's merge of main and a rebase keep it; another change does not"
    assert gh.patch_id(work, 1, c4) not in (None, gh.patch_id(work, 1, c1)), "a change that differs only in whitespace is another change"
    assert gh.patch_id(work, 1, "f" * 40) is None
    # a merge that only brings main in leads back to the head it merged; a plain commit, a merge with an edit of its own, a merge of something not on main do not
    assert gh.main_merge_parent(work, 1, c2) == c1 and gh.main_merge_parent(work, 1, c1) is None
    g(work, "checkout", "-q", "-b", "evil", c1); g(work, "merge", "-q", "--no-commit", "origin/main"); open(os.path.join(work, "x.txt"), "w").write("sneaked in\n"); g(work, "add", "x.txt"); g(work, "commit", "-q", "--no-edit")
    g(work, "checkout", "-q", "-b", "side", "main"); side = commit(work, "s.txt", "s\n", "not on main")
    g(work, "checkout", "-q", "-b", "notmain", c1); g(work, "merge", "-q", "--no-edit", side)
    assert gh.main_merge_parent(work, 1, g(work, "rev-parse", "evil")) is None and gh.main_merge_parent(work, 1, g(work, "rev-parse", "notmain")) is None
    assert gh.patch_id(os.path.join(d, "nowhere"), 1, c1) is None
    g(work, "checkout", "-q", "main")

    # the ssh update: a clean merge is pushed; a conflict pushes nothing; no branch, no origin: error; a foreign directory is never deleted; the scratch worktree goes away
    for name, (path, text) in {"pr-clean": ("b.txt", "b\n"), "pr-conflict": ("a.txt", "mine\n")}.items():
        g(work, "checkout", "-q", "-B", name, "main"); commit(work, path, text, name); g(work, "push", "-q", "origin", name)
    g(work, "checkout", "-q", "main"); commit(work, "a.txt", "main moved\n", "main moves"); g(work, "push", "-q", "origin", "main")
    wt = os.path.join(d, "mq-wt")
    os.makedirs(wt); open(os.path.join(wt, "junk"), "w").write("x")   # left by a killed run
    assert mq.update_over_ssh(work, "pr-clean", wt) == "ok" and not os.path.exists(wt)
    g(work, "fetch", "-q", "origin")
    assert g(work, "merge-base", "--is-ancestor", "origin/main", "origin/pr-clean") == "" and "mq-wt" not in g(work, "worktree", "list")
    before = g(work, "rev-parse", "origin/pr-conflict")
    assert mq.update_over_ssh(work, "pr-conflict", wt) == "conflict" and (g(work, "fetch", "-q", "origin"), g(work, "rev-parse", "origin/pr-conflict"))[1] == before
    assert mq.update_over_ssh(work, "no-such-branch", wt) == "error"
    lonely = os.path.join(d, "lonely"); g(d, "init", "-q", "-b", "main", lonely)
    assert mq.update_over_ssh(lonely, "x", wt) == "error"
    other = os.path.join(d, "not-ours"); os.makedirs(other); open(os.path.join(other, "keep"), "w").write("x")
    assert mq.update_over_ssh(work, "pr-clean", other) == "error" and os.path.exists(os.path.join(other, "keep"))

    # the main checkout after a merge: fast-forwarded when clean and on main; otherwise ALERT and both lanes paused; an unreachable origin only logged
    def checkout(name, setup=None):
        path = os.path.join(d, "main-" + name)
        g(d, "clone", "-q", origin, path)
        if setup:
            setup(path)
        commit(work, "a.txt", name + "\n", name); g(work, "push", "-q", "origin", "main")   # origin moves on
        gh = FakeGh(); gh.add(201)
        proc, q, log, slept = _proc(mq, d, "ff-" + name, gh, [201], main_checkout=path)
        return proc, log, slept, path
    proc, log, _, path = checkout("ok")
    proc.ff_main(201, 0)
    assert "FF-MAIN" in events(log) and "ALERT" not in events(log) and g(path, "rev-parse", "HEAD") == g(path, "rev-parse", "origin/main")
    for name, setup in (("branch", lambda p: g(p, "checkout", "-q", "-b", "work")), ("dirty", lambda p: open(os.path.join(p, "m.txt"), "w").write("mine\n")),
                        ("local", lambda p: commit(p, "z.txt", "z\n", "local only"))):
        proc, log, _, path = checkout(name, setup)
        before = g(path, "rev-parse", "HEAD")
        proc.ff_main(201, 0)
        assert "ALERT" in events(log) and proc.pause.is_paused() and g(path, "rev-parse", "HEAD") == before, (name, log.lines)
    proc.pause.resume([])
    assert not proc.pause.is_paused()
    proc, log, _, path = checkout("gone", lambda p: g(p, "remote", "set-url", "origin", os.path.join(d, "nowhere.git")))
    proc.ff_main(201, 0)
    assert "ALERT" in events(log) and not proc.pause.is_paused()
    # under its own lock; a git command that fails on another's index.lock is tried again
    proc, log, slept, path = checkout("lock")
    real_locked, seen = proc.queue.locked, []
    proc.queue.locked = lambda suffix=".lock": (seen.append(suffix), real_locked(suffix))[1]
    real_sh, tries = mq.sh, []
    mq.sh = lambda args, **kw: (tries.append(1), subprocess.CompletedProcess(args, 128, "", "index.lock exists"))[1] if "--ff-only" in args and len(tries) < 2 else real_sh(args, **kw)
    try:
        proc.ff_main(201, 0)
    finally:
        mq.sh = real_sh
    assert seen == [".ff-lock"] and len(tries) == 2 and "FF-MAIN" in events(log) and slept.count(5) == 2
    # the main checkout is the first worktree, found from a worktree too; a bare repository has none
    g(path, "worktree", "add", "-q", os.path.join(d, "ff-wt"), "-b", "other")
    cwd = os.getcwd()
    try:
        os.chdir(os.path.join(d, "ff-wt"))
        assert os.path.realpath(mq.main_checkout()) == os.path.realpath(path)
        os.chdir(origin)
        assert mq.main_checkout() is None
    finally:
        os.chdir(cwd)


def _docs_tiers(mq, d):
    # the lane function: docs only when relevant_change.py prints tier=docs and exits 0; anything else is the stricter code lane
    repo = os.path.join(d, "tiers")
    os.makedirs(os.path.join(repo, "scripts/ci"))
    script = os.path.join(repo, "scripts/ci/relevant_change.py")
    files = lambda f: type("F", (), {"files": lambda self, n: f})()
    open(script, "w").write("import sys\nprint('tier=' + ('docs' if all(p.startswith(('docs/', '.claude/')) for p in open(sys.argv[2]).read().split()) else 'code'))\n")
    lane = mq.default_lane(repo)
    assert lane(files(["docs/a.md"]), 1) == "docs" and lane(files(["docs/a.md", "backend/pom.xml"]), 1) == "code" and lane(files(None), 1) == "code" and lane(files([]), 1) == "code"
    assert lane(files(["docs/a.md", ".claude/rules/x.md"]), 1) == "code", "a guarded path is never the docs lane, whatever its tier (the fake tier calls .claude/ docs)"
    open(script, "w").write("print('tier=docs')\nraise SystemExit(3)\n")
    assert mq.default_lane(repo)(files(["docs/a.md"]), 1) == "code", "a non-zero exit is never docs"

    # the verdict the queue relies on, from the real relevant_change.py: a doc a test reads is never tier=docs
    root = os.path.abspath(os.path.join(mq.HERE, "..", ".."))
    check = mq.docs_only(root)
    spec = importlib.util.spec_from_file_location("relevant_change", os.path.join(root, "scripts", "ci", "relevant_change.py"))
    rc = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(rc)
    tracked = subprocess.run(["git", "-c", "core.quotepath=off", "ls-files", "docs"], cwd=root, capture_output=True, text=True).stdout.split()
    plain = next(f for f in tracked if f.endswith(".md") and not rc.is_relevant(f))
    index, light = rc.ReaderIndex(root), rc.light_class_names(root)
    guarded = next((f for f in tracked if rc.is_guarded_doc(f) and (rc.plan_for([f], index, light) or {}).get("classes")), None)
    assert guarded, "no doc of the guarded tier is left: the guarded case would silently vanish; pick the sample differently"
    assert check([plain])
    for read in ["docs/architecture/events/asyncapi.yaml", "docs/glossary/terms.yaml", guarded]:
        assert not check([read]) and not check([plain, read]), f"{read} is read by a test: not docs"
