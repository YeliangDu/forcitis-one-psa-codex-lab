"""Self-test of gapscan.py, run by mq_selftest.py (`mq.py --self-test`): the reasons, the once-per-window report, gaps.md, and the scan thread of the runner."""
import datetime
import json
import os


def run(mq, d):
    import gapscan
    now = datetime.datetime(2026, 10, 22, 20, 0, tzinfo=datetime.timezone.utc)
    at = lambda minutes: (now - datetime.timedelta(minutes=minutes)).isoformat().replace("+00:00", "Z")
    owners = {"one-a": "one-a", "one-a-b": "one-a-b"}

    def pr(n, head="a" * 40, idle=5, mergeable="MERGEABLE", rollup=(), comments=(), draft=False):
        return dict(number=n, title=f"pr {n}", headRefName="claude/one-a-x", headRefOid=head, isDraft=draft, mergeable=mergeable, statusCheckRollup=list(rollup), commits=[dict(committedDate=at(idle))], comments=list(comments), createdAt=at(idle))
    check = lambda name, conclusion, started: dict(name=name, workflowName="ci", conclusion=conclusion, startedAt=at(started))
    ok = lambda head, minutes=1, author=gapscan.APPROVER: dict(author=author, createdAt=at(minutes), body=f"调度批准入队 head {head}")
    assert gapscan.approved_head([ok("ABCDEF123")], "abcdef123" + "0" * 31)
    assert gapscan.author_session("claude/one-a-b-thing", owners) == "one-a-b" and gapscan.author_session("claude/one-ab", owners) == "?(claude/one-ab)", "a prefix ends at a dash"
    for rollup, want in (([check("backend verify", "FAILURE", 30), check("backend verify", "SUCCESS", 10)], []), ([check("backend verify", "SUCCESS", 30), check("backend verify", "FAILURE", 10)], ["backend verify"]),
                         ([check("backend verify", "FAILURE", 30), check("backend verify", "CANCELLED", 10)], ["backend verify"]), ([check("backend verify", "FAILURE", 30), dict(check("backend verify", "", 10), status="IN_PROGRESS")], []),
                         ([check("assertion drift", "FAILURE", 10), check("backend verify (light)", "FAILURE", 10)], []), ([dict(context="secret scan", state="ERROR")], ["secret scan"])):
        assert gapscan.red_checks(rollup) == want, rollup
    head = "b" * 40
    prs = [pr(1, mergeable="CONFLICTING"), pr(2, rollup=[check("backend verify", "FAILURE", 10)]), pr(3, head=head, comments=[ok(head[:9])]), pr(4, idle=61), pr(5, idle=59), pr(6, idle=600, draft=True),
           pr(7, idle=600, mergeable="CONFLICTING"), pr(8, head=head, comments=[ok("c" * 9)]), pr(9, head=head, comments=[ok(head[:9], author="x")]), pr(10, head=head, comments=[ok("c" * 9, 20), ok(head[:9], 5)]),
           pr(11, head=head, comments=[ok(head[:9], 20), ok("c" * 9, 5)])]
    gaps = gapscan.find_gaps(prs, {7}, now, owners)
    assert sorted((g["pr"], g["reason"]) for g in gaps) == [(1, "CONFLICT"), (2, "CI_RED"), (3, "APPROVED_NOT_QUEUED"), (4, "STALE"), (10, "APPROVED_NOT_QUEUED")]
    assert gapscan.find_gaps([pr(21)], {21}, now, owners) == [], "queued without an approval comment: no gap"
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
    assert len(gapscan.scan(listing(prs), q, directory, log, now=now, owners=owners)) == 5 and gapscan.scan(listing(prs), q, directory, log, now=now, owners=owners) == []
    text = open(os.path.join(directory, "gaps.md"), encoding="utf-8").read()
    assert "* #1 [one-a] CONFLICT" in text and text.count("* #") == 5 and sum(" GAPS " in l for l in log.lines) == 1
    before = open(os.path.join(directory, "gaps.state.json")).read()
    assert gapscan.scan(listing(None), q, directory, log, now=now, owners=owners) is None and open(os.path.join(directory, "gaps.state.json")).read() == before, "gh failing forgets nothing"
    calls, slept = [], []

    def boom():
        calls.append(1)
        if len(calls) == 1:
            raise RuntimeError("boom")
        return []
    proc = mq.Processor(type("B", (), {"open_prs": lambda self: boom()})(), q, log, "/repo", sleep=slept.append, lane=lambda g, n: "code", docs_only_fn=lambda f: False)
    mq.run_scan(proc, directory, every=1800, first=60, stop=lambda: len(slept) > 3)
    assert slept[:3] == [60, 1800, 1800] and len(calls) == 3 and any("gap scan: RuntimeError: boom" in l for l in log.lines), "scans on its own clock and survives a failing scan"
    # the owner comes from policy.json sessions.config alone (#856: no aliases); an old or unknown name is reported, not guessed
    owners = gapscan.load_owners()
    for branch, owner in (("claude/one-dev-org-753", "one-dev-org"), ("claude/one-dev-identity-9", "one-dev-identity"), ("claude/one-platform-810-x", "one-platform"), ("claude/one-lead-822-x", "one-lead"),
                          ("claude/one-design-860-x", "one-design"), ("claude/one-ops-744-x", "?(claude/one-ops-744-x)"),
                          ("claude/one-architecture-812-x", "?(claude/one-architecture-812-x)"), ("claude/one-opsx-1", "?(claude/one-opsx-1)")):
        assert gapscan.author_session(branch, owners) == owner, (branch, gapscan.author_session(branch, owners))
    policy = json.load(open(gapscan.POLICY, encoding="utf-8"))["sessions"]
    assert set(owners) == set(owners.values()) == set(policy["config"]) and "aliases" not in policy, "owners are exactly the sessions.config keys"
    run_cards(mq, d)


def run_cards(mq, d):
    """CARD_OPEN: an open card whose last pull request merged and that no open pull request names (first line, issue_gate.FIRST_LINE)."""
    import gapscan
    import mq_selftest
    now = datetime.datetime(2026, 11, 4, 12, 0, tzinfo=datetime.timezone.utc)
    later = lambda **kw: now + datetime.timedelta(**kw)
    merged = lambda n, body, minutes=5: dict(number=n, body=body, mergedAt=(now - datetime.timedelta(minutes=minutes)).isoformat().replace("+00:00", "Z"))
    card = lambda title="t", state="OPEN", *labels: dict(state=state, labels=list(labels), title=title)

    class Gh:
        def __init__(self, merged_rows, cards, opened=()):
            self.merged_rows, self.cards, self.opened, self.since, self.card_calls = merged_rows, cards, list(opened), None, []

        def open_prs(self):
            return self.opened

        def merged_prs(self, since):
            self.since = since
            return self.merged_rows

        def card(self, n):
            self.card_calls.append(n)
            return self.cards.get(n)

    def open_pr(n, body):
        return dict(number=n, title=f"pr {n}", headRefName="claude/one-a-x", headRefOid="a" * 40, isDraft=False, mergeable="MERGEABLE", statusCheckRollup=[], commits=[dict(committedDate=now.isoformat())], comments=[], body=body)
    owners = {"one-a": "one-a"}
    directory = os.path.join(d, "cardscan")
    q = mq.Queue(os.path.join(directory, "merge.queue"))
    log = mq.Log(os.path.join(directory, "merge.log"), echo=False)
    cards = {10: card("open card", "OPEN", "会话:one-a"), 11: card("closed", "CLOSED", "会话:one-a"), 12: card("has an open pr", "OPEN", "会话:one-a"), 13: card("below first line", "OPEN", "会话:one-a"),
             14: card("held", "OPEN", "会话:one-a", "挂起"), 15: card("no label", "OPEN")}
    rows = [merged(100, "Refs #10\nbody"), merged(101, "Closes #11\n"), merged(102, "Refs #12\n"), merged(103, "summary\nRefs #13\n"), merged(104, "Refs #14\n"), merged(105, "Refs #15\n"), merged(106, "no card here"), merged(107, "Refs #10\n", 1)]
    gh = Gh(rows, cards, [open_pr(200, "Refs #12\n")])
    got = gapscan.scan(gh, q, directory, log, now=now, owners=owners)
    assert [(g["pr"], g["session"], g["reason"], g["detail"]) for g in got] == [(10, "one-a", "CARD_OPEN", "last PR #107 merged, no open PR"), (15, "unknown", "CARD_OPEN", "last PR #105 merged, no open PR")], got
    assert gh.since == now - datetime.timedelta(seconds=gapscan.REPEAT_AFTER), "no marker: look back REPEAT_AFTER"
    text = open(os.path.join(directory, "gaps.md"), encoding="utf-8").read()
    assert "* #10 [one-a] CARD_OPEN: last PR #107 merged, no open PR (open card)" in text and "#11" not in text and "#12" not in text and "#13" not in text and "#14" not in text
    state = json.load(open(os.path.join(directory, "gaps.state.json")))
    assert state["_merged_since"] == now.isoformat(timespec="seconds") and sorted(state["_cards"]) == ["10", "14", "15"], state
    # nothing new merged: the candidates stay, are not reported again inside REPEAT_AFTER, and are reported again after it; the marker survives select_new's clean-up
    gh.merged_rows, gh.opened = [], []
    assert gapscan.scan(gh, q, directory, log, now=later(minutes=30), owners=owners) == [] and gh.since == now, "the next scan looks since the marker"
    assert [g["pr"] for g in gapscan.scan(gh, q, directory, log, now=later(hours=4), owners=owners)] == [10, 15], "a candidate repeats every REPEAT_AFTER"
    assert json.load(open(os.path.join(directory, "gaps.state.json")))["_merged_since"] == later(hours=4).isoformat(timespec="seconds")
    # a candidate leaves when its card closes or an open pull request names it; a held card is looked at again once released
    cards[10] = card("open card", "CLOSED", "会话:one-a")
    gh.opened = [open_pr(201, "Refs #15\n")]
    cards[14] = card("held", "OPEN", "会话:one-a")
    assert [g["pr"] for g in gapscan.scan(gh, q, directory, log, now=later(hours=8), owners=owners)] == [14], "closed, named by an open pull request, released"
    assert sorted(json.load(open(os.path.join(directory, "gaps.state.json")))["_cards"]) == ["14"]
    # a cut open list, a failing merged list and a failing card read report nothing and move no marker
    cut = Gh([merged(300, "Refs #10\n")], {10: card("c", "OPEN")}, [open_pr(1000 + i, "x") for i in range(gapscan.OPEN_LIMIT)])
    assert gapscan.scan(cut, q, directory, log, now=later(hours=9), owners=owners) is not None and cut.since is None, "open list cut: CARD_OPEN skipped, merged list not even asked"
    assert json.load(open(os.path.join(directory, "gaps.state.json")))["_merged_since"] == later(hours=8).isoformat(timespec="seconds")
    bad = Gh(None, {})
    assert gapscan.scan(bad, q, directory, log, now=later(hours=10), owners=owners) == [] and any("gh pr list --state merged failed" in l for l in log.lines)
    assert json.load(open(os.path.join(directory, "gaps.state.json")))["_merged_since"] == later(hours=8).isoformat(timespec="seconds")
    unreadable = Gh([merged(301, "Refs #16\n")], {})
    assert gapscan.scan(unreadable, q, directory, log, now=later(hours=11), owners=owners) == [] and "16" in json.load(open(os.path.join(directory, "gaps.state.json")))["_cards"], "a card gh cannot read stays a candidate"
    # the real Gh against the fake gh binary
    mq_selftest.fake_gh(d, [(["pr", "list", "--state", "merged"], json.dumps([{"number": 1, "body": "Refs #2", "mergedAt": "t"}]), "", 0), (["issue", "view", "2"], json.dumps({"state": "OPEN", "labels": [{"name": "会话:one-a"}], "title": "x"}), "", 0),
                            (["issue", "view", "3"], "", "down", 1)])
    real = mq.Gh()
    assert real.merged_prs(now) == [{"number": 1, "body": "Refs #2", "mergedAt": "t"}] and real.card(2) == {"state": "OPEN", "labels": ["会话:one-a"], "title": "x"} and real.card(3) is None
    calls = [json.loads(l) for l in open(os.environ["FAKE_GH_LOG"]).read().splitlines()]
    assert ["pr", "list", "--state", "merged", "--search", "merged:>=2026-11-04T12:00:00Z", "--limit", str(gapscan.MERGED_LIMIT), "--json", "number,body,mergedAt"] in calls
    mq_selftest.fake_gh(d, [])
