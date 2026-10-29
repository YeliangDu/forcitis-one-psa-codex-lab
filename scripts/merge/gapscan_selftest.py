"""Self-test of gapscan.py, run by mq_selftest.py (`mq.py --self-test`): the reasons, the once-per-window report, gaps.md, and the scan thread of the runner."""
import datetime
import os


def run(mq, d):
    import gapscan
    now = datetime.datetime(2026, 10, 22, 20, 0, tzinfo=datetime.timezone.utc)
    at = lambda minutes: (now - datetime.timedelta(minutes=minutes)).isoformat().replace("+00:00", "Z")
    prefixes = {"claude/one-a": "one-a", "claude/one-a-b": "one-a-b"}

    def pr(n, head="a" * 40, idle=5, mergeable="MERGEABLE", rollup=(), comments=(), draft=False):
        return dict(number=n, title=f"pr {n}", headRefName="claude/one-a-x", headRefOid=head, isDraft=draft, mergeable=mergeable, statusCheckRollup=list(rollup), commits=[dict(committedDate=at(idle))], comments=list(comments), createdAt=at(idle))
    check = lambda name, conclusion, started: dict(name=name, workflowName="ci", conclusion=conclusion, startedAt=at(started))
    ok = lambda head, minutes=1, author=gapscan.APPROVER: dict(author=author, createdAt=at(minutes), body=f"调度批准入队 head {head}")
    assert gapscan.approved_head([ok("ABCDEF123")], "abcdef123" + "0" * 31)
    assert gapscan.author_session("claude/one-a-b-thing", prefixes) == "one-a-b" and gapscan.author_session("claude/one-ab", prefixes) == "?(claude/one-ab)", "a prefix ends at a dash"
    for rollup, want in (([check("backend verify", "FAILURE", 30), check("backend verify", "SUCCESS", 10)], []), ([check("backend verify", "SUCCESS", 30), check("backend verify", "FAILURE", 10)], ["backend verify"]),
                         ([check("backend verify", "FAILURE", 30), check("backend verify", "CANCELLED", 10)], ["backend verify"]), ([check("backend verify", "FAILURE", 30), dict(check("backend verify", "", 10), status="IN_PROGRESS")], []),
                         ([check("assertion drift", "FAILURE", 10), check("backend verify (light)", "FAILURE", 10)], []), ([dict(context="secret scan", state="ERROR")], ["secret scan"])):
        assert gapscan.red_checks(rollup) == want, rollup
    head = "b" * 40
    prs = [pr(1, mergeable="CONFLICTING"), pr(2, rollup=[check("backend verify", "FAILURE", 10)]), pr(3, head=head, comments=[ok(head[:9])]), pr(4, idle=61), pr(5, idle=59), pr(6, idle=600, draft=True),
           pr(7, idle=600, mergeable="CONFLICTING"), pr(8, head=head, comments=[ok("c" * 9)]), pr(9, head=head, comments=[ok(head[:9], author="x")]), pr(10, head=head, comments=[ok("c" * 9, 20), ok(head[:9], 5)]),
           pr(11, head=head, comments=[ok(head[:9], 20), ok("c" * 9, 5)])]
    gaps = gapscan.find_gaps(prs, {7}, now, prefixes)
    assert sorted((g["pr"], g["reason"]) for g in gaps) == [(1, "CONFLICT"), (2, "CI_RED"), (3, "APPROVED_NOT_QUEUED"), (4, "STALE"), (10, "APPROVED_NOT_QUEUED")]
    assert gapscan.find_gaps([pr(21)], {21}, now, prefixes) == [], "queued without an approval comment: no gap"
    state = {}
    assert len(gapscan.select_new(gaps, state, now)) == 5 and gapscan.select_new(gaps, state, now + datetime.timedelta(hours=3, minutes=59)) == [], "once in 4 hours"
    assert len(gapscan.select_new(gaps, state, now + datetime.timedelta(hours=4))) == 5, "reported again once the window ran out"
    gapscan.select_new([g for g in gaps if g["reason"] != "CONFLICT"], state, now + datetime.timedelta(hours=4, minutes=30))
    assert gapscan.select_new(gaps, state, now + datetime.timedelta(hours=5)) == [], "back inside its window after a flicker: not new"
    assert len(gapscan.select_new(gaps, {"#1:CONFLICT": 12345}, now)) == 5, "a state value that is not a time is no report"
    directory = os.path.join(d, "gapscan")
    q = mq.Queue(os.path.join(directory, "merge.queue")); q.enqueue(7)
    log = mq.Log(os.path.join(directory, "merge.log"), echo=False)
    listing = lambda rows: type("L", (), {"open_prs": lambda self: rows})()
    assert len(gapscan.scan(listing(prs), q, directory, log, now=now, prefixes=prefixes)) == 5 and gapscan.scan(listing(prs), q, directory, log, now=now, prefixes=prefixes) == []
    text = open(os.path.join(directory, "gaps.md"), encoding="utf-8").read()
    assert "* #1 [one-a] CONFLICT" in text and text.count("* #") == 5 and sum(" GAPS " in l for l in log.lines) == 1
    before = open(os.path.join(directory, "gaps.state.json")).read()
    assert gapscan.scan(listing(None), q, directory, log, now=now, prefixes=prefixes) is None and open(os.path.join(directory, "gaps.state.json")).read() == before, "gh failing forgets nothing"
    calls, slept = [], []

    def boom():
        calls.append(1)
        if len(calls) == 1:
            raise RuntimeError("boom")
        return []
    proc = mq.Processor(type("B", (), {"open_prs": lambda self: boom()})(), q, log, "/repo", sleep=slept.append, lane=lambda g, n: "code", docs_only_fn=lambda f: False)
    mq.run_scan(proc, directory, every=1800, first=60, stop=lambda: len(slept) > 3)
    assert slept[:3] == [60, 1800, 1800] and len(calls) == 3 and any("gap scan: RuntimeError: boom" in l for l in log.lines), "scans on its own clock and survives a failing scan"
    mapped = gapscan.load_prefixes()
    profiles = {f[:-3] for f in os.listdir(os.path.join(mq.HERE, "..", "..", "docs", "modernization", "sessions")) if f.startswith("one-") and f.endswith(".md")}
    assert profiles <= set(mapped.values()), profiles - set(mapped.values())
