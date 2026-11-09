#!/usr/bin/env python3
"""Self-test of pr_mix.py (#1562): the counts by kind from a fake gh, the time filter, the Codex line count for backend runtime PRs, the output shape, and a gh that cannot answer.
Usage: pr_mix_selftest.py (also pr_mix.py --self-test)"""
import contextlib
import datetime
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import pr_mix  # noqa: E402


def merged(number, paths, at, body=""):
    return {"number": number, "title": f"secret title {number}", "body": body, "merged_at": at, "paths": paths}


class FakeGh:
    def __init__(self, prs):
        self.prs, self.asked = prs, []

    def merged_since_day(self, since_day):
        self.asked.append(since_day)
        return self.prs


def run_main(prs, *argv):
    out, gh = io.StringIO(), FakeGh(prs)
    with contextlib.redirect_stdout(out):
        code = pr_mix.main(list(argv), gh=gh)
    return code, out.getvalue(), gh


def run():
    since = datetime.datetime(2026, 11, 2, 9, 0, tzinfo=datetime.timezone.utc)
    prs = [
        merged(1, ["backend/src/main/A.java"], "2026-11-03T10:00:00Z", "Codex: 01a18901-dd25-71b0-addd-7a5fcb6d205b"),
        merged(2, ["backend/src/main/B.java", "backend/src/test/BTest.java"], "2026-11-03T11:00:00Z", "Closes #2"),
        merged(3, ["frontend/apps/web/src/a.tsx"], "2026-11-03T12:00:00Z", "Codex: skip small"),   # frontend runtime: counted as runtime, not as backend
        merged(4, ["backend/src/test/CTest.java"], "2026-11-03T13:00:00Z"),
        merged(5, ["scripts/merge/mq.py"], "2026-11-03T14:00:00Z"),
        merged(6, ["docs/adr/a.md"], "2026-11-03T15:00:00Z"),
        merged(7, ["docs/decisions/2026-11-03-001-x.md"], "2026-11-03T16:00:00Z"),
        merged(8, ["docs/rules/rulings/r.md", "docs/decisions/x.md"], "2026-11-03T17:00:00Z"),
        merged(9, ["docs/rules/rules.md"], "2026-11-03T18:00:00Z"),
        merged(10, ["backend/src/main/C.java"], "2026-11-02T08:59:59Z", "Codex: x"),   # before `since`: not counted
        merged(11, ["backend/src/main/D.java"], "2026-11-02T09:00:00Z"),   # exactly at `since`: counted
        merged(12, [], "2026-11-04T09:00:00Z"),
    ]
    counts, total, backend = pr_mix.mix(prs, since)
    assert counts == {"runtime": 4, "test": 1, "scripts": 1, "docs": 2, "records": 2, "rules": 1} and total == 11, (counts, total)
    assert backend == (3, 1), backend   # #1, #2, #11 change backend/src/main; only #1 has a Codex session line (`skip small` is a line too, but #3 is not backend)
    assert pr_mix.mix([merged(1, ["backend/src/main/A.java"], "2026-11-03T10:00:00Z", "Codex: skip small")], since)[2] == (1, 1), "a skip line is a Codex line"
    assert pr_mix.mix([merged(1, ["backend/src/main/A.java"], "2026-11-03T10:00:00Z", "```\nCodex: x\n```\nuses Codex: inline")], since)[2] == (1, 0), "a quotation is no line"
    assert pr_mix.mix([], since) == (dict.fromkeys(pr_mix.pr_kinds.KINDS, 0), 0, (0, 0))
    code, out, gh = run_main(prs, "--since", "2026-11-02T09:00:00Z")
    assert code == 0 and gh.asked == ["2026-11-01"], gh.asked   # the search is by UTC day: a day early, the filter is in the code
    assert out.splitlines()[0] == "merged since 2026-11-02T09:00:00+00:00: 11", out
    for kind, n in (("runtime", 4), ("test", 1), ("scripts", 1), ("docs", 2), ("records", 2), ("rules", 1)):
        assert f"  {kind:8} {n:4}" in out.splitlines(), (kind, out)
    assert out.splitlines()[-1] == "runtime backend PRs (change backend/src/main): 3, with a `Codex:` line: 1", out
    assert "secret title" not in out and "Codex: x" not in out, "aggregates only"
    assert run_main(prs, "--since", "2026-11-02T09:00:00")[1] == out, "no zone means UTC"
    for bad in ("yesterday", "2026-13-40", ""):
        try:
            run_main(prs, "--since", bad)
            raise AssertionError(bad)
        except SystemExit as e:
            assert "ISO time" in str(e), e
    try:
        run_main(None, "--since", "2026-11-02T09:00:00Z")
        raise AssertionError("gh that cannot answer must be an error, not zero counts")
    except SystemExit as e:
        assert "cannot list" in str(e)
    print("pr_mix_selftest: ok")
    return 0


if __name__ == "__main__":
    sys.exit(run())
