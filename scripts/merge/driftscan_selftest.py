"""Self-test of driftscan.py (`driftscan.py --self-test`, and run by mq_selftest.py): every reason has a red and a clean case; sources are faked, nothing reaches gh, git or ps."""
import datetime
import json
import os
import tempfile

import driftscan
from mq_queue import Log, Queue

NOW = datetime.datetime(2026, 10, 30, 12, 0, tzinfo=datetime.timezone.utc)
SESSIONS = {"one-lead", "one-platform", "one-design"}
OWNERS = {name: name for name in SESSIONS}


def at(minutes):
    return (NOW - datetime.timedelta(minutes=minutes)).isoformat(timespec="seconds")


def pr(n, branch="claude/one-platform-x", commit=600, draft=False):
    return dict(number=n, headRefName=branch, isDraft=draft, commits=[dict(committedDate=at(commit).replace("+00:00", "Z"))])


def event(minutes, ev, n, detail=""):
    return f"{at(minutes)} {ev} pr=#{n} run=- elapsed=0s lane=code {detail}".rstrip()


class Fake:
    def __init__(self, **kw):
        self.kw = dict(prs=[], issues=[], worktrees=[("/w/a", "one-platform")], started=None, changed=NOW, handoff="", states={})
        self.kw.update(kw)
    open_prs = lambda self: self.kw["prs"]
    issues = lambda self: self.kw["issues"]
    worktrees = lambda self: self.kw["worktrees"]
    runner_started = lambda self: self.kw["started"]
    main_changed = lambda self: self.kw["changed"]
    handoff = lambda self: self.kw["handoff"]
    states = lambda self, numbers: self.kw["states"]


def reasons(findings):
    return sorted((r, s) for r, s, _ in findings)


def run(d=None):
    # DROPPED
    log = [event(300, "START", 1), event(200, "DEQUEUE", 1, "CONFLICT"), event(300, "START", 2), event(200, "DEQUEUE", 2, "MERGED"), event(300, "START", 3), event(200, "DEQUEUE", 3, "HOLD"),
           event(300, "START", 4), event(200, "DEQUEUE", 4, "HOLD"), event(100, "START", 4), event(300, "START", 5), event(200, "DEQUEUE", 5, "HOLD"), event(300, "START", 6), event(200, "DEQUEUE", 6, "HOLD"), event(190, "GAPS", 6)]
    prs = [pr(1), pr(2), pr(3, commit=100), pr(4), pr(5, draft=True), pr(6), pr(7)]
    assert reasons(driftscan.find_dropped(prs, {6}, log)) == [("DROPPED", "#1")], "dropped: left the queue unmerged, nothing pushed; merged, pushed since, taken again, draft, queued and never queued are not"
    assert driftscan.find_dropped(prs, set(), []) == [], "no log, no finding"
    # markers
    found = driftscan.find_marker_drift([("/w/1", None), ("/w/2", "one-old"), ("/w/3", "one-lead"), ("/w/4", "one-platform"), ("/w/5", "one-platform")], SESSIONS)
    assert reasons(found) == [("MARKER_DUPLICATE", "one-platform"), ("MARKER_MISSING", "/w/1"), ("MARKER_UNKNOWN", "/w/2")], found
    assert driftscan.find_marker_drift([("/w/3", "one-lead"), ("/w/4", "one-platform")], SESSIONS) == []
    # runner
    early, late = NOW - datetime.timedelta(hours=2), NOW - datetime.timedelta(hours=1)
    stale = driftscan.find_runner_stale(early, late)
    assert [r for r, _, _ in stale] == ["RUNNER_STALE"] and early.isoformat(timespec="seconds") in stale[0][2] and late.isoformat(timespec="seconds") in stale[0][2] and driftscan.RESTART in stale[0][2]
    assert driftscan.find_runner_stale(late, early) == [] and driftscan.find_runner_stale(None, late) == [] and driftscan.find_runner_stale(early, None) == []
    # owners
    label = lambda *names: [dict(name=n) for n in names]
    issues = [dict(number=1, labels=label("会话:one-design")), dict(number=2, labels=label("会话:one-platform")), dict(number=3, labels=label("会话:one-design", "挂起")), dict(number=4, labels=label("会话:one-design", "Hold")),
              dict(number=5, labels=label("bug"))]
    assert reasons(driftscan.find_owner_absent(issues, {"one-platform"})) == [("OWNER_ABSENT", "#1")]
    # handoff
    text = "类型：docs\n- FIRST: arm Monitor, #801 is background\n- In flight: #865 drift, PR #866 queued, #700\n- Open follow-ups: #859 leftover\nWaiting on user: #717\n- Waiting on user: #718\n"
    assert driftscan.cited_open(text) == [700, 717, 718, 859, 865, 866], driftscan.cited_open(text)
    states = {700: "closed", 718: "open", 859: "open", 865: "open", 866: "closed"}
    lead = [pr(900, "claude/one-lead-x"), pr(865, "claude/one-lead-y"), pr(901, "claude/one-platform-z")]
    assert reasons(driftscan.find_handoff_stale(text, states, lead, OWNERS)) == [("HANDOFF_STALE", "#700"), ("HANDOFF_STALE", "#866"), ("HANDOFF_STALE", "#900")]
    assert reasons(driftscan.find_handoff_stale(None, {}, lead, OWNERS)) == [("HANDOFF_STALE", "handoff")], "a missing handoff is one finding"
    assert driftscan.find_handoff_stale("- Open: #1\n", {1: "open"}, [], OWNERS) == []
    # all sources together, a source that cannot answer, and the clean case
    src = Fake(prs=[pr(1)], worktrees=[("/w/1", None)], started=early, changed=late, handoff="- Open: #5\n", states={5: "closed"})
    findings, errors = driftscan.check(src, set(), log, OWNERS, SESSIONS)
    assert sorted({r for r, _, _ in findings}) == ["DROPPED", "HANDOFF_STALE", "MARKER_MISSING", "RUNNER_STALE"] and errors == [], findings
    findings, errors = driftscan.check(Fake(prs=None, issues=None, worktrees=None, changed=None, handoff="- Open: #5\n"), set(), log, OWNERS, SESSIONS)
    assert findings == [] and errors == ["pull requests", "issues", "worktrees", "main history", "handoff cross-check"], (findings, errors)
    assert driftscan.check(Fake(), set(), [], OWNERS, SESSIONS) == ([], []), "clean"
    # once per window
    state = {}
    two = [("DROPPED", "#1", "x"), ("MARKER_MISSING", "/w/1", "y")]
    assert driftscan.select_new(two, state, NOW) == two and driftscan.select_new(two, state, NOW + datetime.timedelta(hours=23, minutes=59)) == []
    assert driftscan.select_new(two, state, NOW + datetime.timedelta(hours=24)) == two
    assert driftscan.select_new(two, {"DROPPED #1": 5}, NOW) == two, "a state value that is not a time is no report"
    # scan: DRIFT line only when new, drift.md, state; a source that fails is logged
    with tempfile.TemporaryDirectory() as tmp:
        directory = os.path.join(d or tmp, "driftscan")
        os.makedirs(directory, exist_ok=True)
        q = Queue(os.path.join(directory, "merge.queue"))
        with open(os.path.join(directory, "merge.log"), "w", encoding="utf-8") as f:
            f.write("\n".join(log) + "\n")
        out = Log(os.path.join(directory, "merge.log"), echo=False)
        out.lines = []
        src = Fake(prs=[pr(1)], worktrees=[("/w/1", None)])
        assert len(driftscan.scan(src, q, directory, out, now=NOW, owners=OWNERS, sessions=SESSIONS)) == 2
        assert driftscan.scan(src, q, directory, out, now=NOW, owners=OWNERS, sessions=SESSIONS) == [], "the second scan has nothing new"
        assert sum(" DRIFT " in l for l in out.lines) == 1 and "DRIFT pr=#- run=- elapsed=0s 2 findings: " in out.lines[0]
        text = open(os.path.join(directory, "drift.md"), encoding="utf-8").read()
        assert "* DROPPED #1 " in text and "* MARKER_MISSING /w/1 " in text
        assert driftscan.scan(Fake(prs=None), q, directory, out, now=NOW, owners=OWNERS, sessions=SESSIONS) == [] and any("drift check: could not read pull requests" in l for l in out.lines)
        assert driftscan.scan(Fake(), q, directory, out, now=NOW, owners=OWNERS, sessions=SESSIONS) == [] and sum(" DRIFT " in l for l in out.lines) == 1, "clean writes no DRIFT line"
    # the real worktree and ps parsing
    m = driftscan.PS_LINE.match("24090 Thu Oct 29 23:52:53 2026 /x/python3 scripts/merge/mq.py run")
    assert m and m.group(1) == "24090" and m.group(3).split()[-2:] == ["scripts/merge/mq.py", "run"]
    # policy.json sessions are what the scan compares markers with
    assert set(driftscan.gapscan.load_owners()) == set(json.load(open(driftscan.gapscan.POLICY, encoding="utf-8"))["sessions"]["config"])


def run_with_mq(mq, d):
    """The drift thread of the runner: first after `first`, then every `every`, survives a failing check."""
    run(d)
    directory = os.path.join(d, "driftthread")
    q = mq.Queue(os.path.join(directory, "merge.queue"))
    log = mq.Log(os.path.join(directory, "merge.log"), echo=False)
    slept, calls = [], []

    class Boom(Fake):
        def open_prs(self):
            calls.append(1)
            if len(calls) == 1:
                raise RuntimeError("boom")
            return []
    proc = mq.Processor(type("B", (), {})(), q, log, "/repo", sleep=slept.append, lane=lambda g, n: "code", docs_only_fn=lambda f: False)
    mq.run_drift(proc, directory, every=86400, first=30, stop=lambda: len(slept) > 3, src=Boom())
    assert slept[:3] == [30, 86400, 86400] and len(calls) == 3 and any("drift check: RuntimeError: boom" in l for l in log.lines), "checks on its own clock and survives a failing check"
