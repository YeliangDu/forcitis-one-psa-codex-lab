#!/usr/bin/env python3
"""Self-test of the records, rules and new-check-file checks at enqueue (mq_records.py, #1562) and of the kinds (pr_kinds.py): a fake gh, a real temporary git repository with an origin, a fixed clock (Friday 2026-11-06 12:00 UTC = 13:00 Berlin).
Usage: mq_records_selftest.py"""
import contextlib
import datetime
import io
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import mq_records  # noqa: E402
import pr_kinds  # noqa: E402

NOW = datetime.datetime(2026, 11, 6, 12, 0, tzinfo=datetime.timezone.utc)
RULES, FRAGMENT, NEW_FRAGMENT = "docs/rules/rules.md", "docs/decisions/2026-11-06-001-x.md", "docs/decisions/2026-11-06-002-y.md"
QUOTED = "docs/rules/roles/lead.md"


def git(cwd, *args):
    r = subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false", *args], cwd=cwd, capture_output=True, text=True)
    assert r.returncode == 0, (args, r.stderr)
    return r.stdout.strip()


class FakeGh:
    def __init__(self, prs, merged=(), issues=None):
        """prs: {number: dict(title, body, files=[(path, previous)], added=[(path, previous)])}."""
        self.prs, self.merged, self.issues = prs, merged, issues if issues is not None else {}
        self.listed = 0

    def view(self, n):
        p = self.prs[n]
        return {"state": p.get("state", "OPEN"), "title": p["title"], "body": p.get("body", ""), "branch": "b", "head": "h", "merge": "CLEAN", "labels": []}

    def file_stats(self, n):
        return [(path, prev, 1, 0) for path, prev in self.prs[n]["files"]]

    def added_paths(self, n):
        return self.prs[n].get("added", [])

    def issue_state(self, n):
        return self.issues.get(n)

    def merged_since_day(self, since):
        self.listed += 1
        return None if self.merged is None else list(self.merged)


def pr(title, paths, body="", added=None):
    return {"title": title, "body": body, "files": [(p, None) for p in paths], "added": added if added is not None else [(p, None) for p in paths if p.startswith("docs/decisions/")]}


def merged(number, paths, at, body=""):
    return {"number": number, "title": "t", "body": body, "merged_at": at, "paths": paths}


def run(root, gh, numbers, queued=()):
    err = io.StringIO()
    try:
        with contextlib.redirect_stderr(err):
            mq_records.enforce(root, gh, numbers, queued, now=NOW)
    except SystemExit as e:
        return str(e), err.getvalue()
    return None, err.getvalue()


def kinds_test():
    k = pr_kinds.kind
    assert k([FRAGMENT, "docs/rules/rulings/a.md"]) == "records" and k([FRAGMENT, "docs/adr/x.md"]) == "docs" and k([]) == "docs"
    assert k([RULES]) == "rules" and k(["CLAUDE.md"]) == "rules" and k(["backend/CLAUDE.md"]) == "rules" and k(["docs/rules/roles/a.md"]) == "rules" and k(["docs/rules/playbooks/a.md"]) == "rules"
    assert k(["docs/rules/coding-core.md"]) == "docs" and k(["frontend/CLAUDE.md"]) == "docs", "only the listed rule files are rule files"
    assert k([RULES, "backend/src/main/A.java"]) == "rules" and k([FRAGMENT, "backend/src/main/A.java"]) == "runtime"
    assert k(["backend/src/main/java/A.java"]) == "runtime" and k(["frontend/apps/web/src/a.tsx"]) == "runtime" and k(["frontend/packages/ui/src/a.ts"]) == "runtime"
    assert k(["frontend/apps/web/src/a.test.tsx"]) == "test" and k(["backend/src/test/java/ATest.java"]) == "test" and k(["frontend/apps/web/e2e/a.ts", "docs/a.md"]) == "test"
    assert k(["scripts/gates/a.py", "backend/src/test/java/ATest.java"]) == "scripts" and k(["policy.json"]) == "scripts" and k([".github/workflows/ci.yml"]) == "scripts" and k(["tools/ops/a.py"]) == "scripts"
    assert k(["docs/adr/a.md", "README.md"]) == "docs" and k(["frontend/package.json"]) == "docs"
    assert pr_kinds.is_check_file("scripts/gates/new_gate.py") and pr_kinds.is_check_file("scripts/hooks/x.sh") and pr_kinds.is_check_file("scripts/guards/g.py")
    assert not pr_kinds.is_check_file("scripts/gates/gates_selftest.py") and not pr_kinds.is_check_file("scripts/guards/x_selftest.py") and not pr_kinds.is_check_file("scripts/gates/codex_cases.py")
    assert not pr_kinds.is_check_file("scripts/guards/null_test.py") and not pr_kinds.is_check_file("scripts/ci/x.py") and not pr_kinds.is_check_file("scripts/merge/mq.py")


def wiring_test(tmp):
    """The entry point runs both checks, and `mq.py enqueue` hands it the pull requests already in the queue."""
    import draft_check
    import mq
    import mq_enqueue
    from unittest import mock
    calls = []
    with mock.patch.object(draft_check, "enforce", lambda *a: calls.append(("draft", a[2]))), mock.patch.object(mq_records, "enforce", lambda root, gh, numbers, queued=(): calls.append(("records", list(numbers), list(queued)))):
        mq_enqueue.enforce("r", object(), [7], [3, 4])
    assert calls == [("draft", [7]), ("records", [7], [3, 4])], calls
    queue = os.path.join(tmp, "queue")
    open(queue, "w").write("5\n6\n")
    seen = []
    def refuse(root, gh, numbers, queued=()):
        seen.append((list(numbers), list(queued)))
        raise SystemExit("nothing queued: stop here")
    with mock.patch.object(mq.mq_title, "enforce", lambda *a: None), mock.patch.object(mq.mq_acceptance, "enforce", lambda *a: None), mock.patch.object(mq.mq_event_payload, "enforce", lambda *a: None), \
            mock.patch.object(mq.mq_enqueue, "enforce", refuse), mock.patch.object(mq, "Gh", lambda: object()):
        try:
            mq.main(["--queue", queue, "enqueue", "9"])
            raise AssertionError("a refusal stops the enqueue")
        except SystemExit as e:
            assert "stop here" in str(e)
    assert seen == [([9], [5, 6])], seen


def main():
    kinds_test()
    with tempfile.TemporaryDirectory() as t:
        wiring_test(t)
    with tempfile.TemporaryDirectory() as tmp:
        tmp = os.path.realpath(tmp)
        origin, root = os.path.join(tmp, "origin.git"), os.path.join(tmp, "main")
        git(tmp, "init", "-q", "--bare", "-b", "main", origin)
        git(tmp, "clone", "-q", origin, root)
        os.makedirs(os.path.join(root, "docs/decisions"))
        open(os.path.join(root, FRAGMENT), "w").write("a fragment\n")
        git(root, "add", "."), git(root, "commit", "-q", "-m", "base"), git(root, "push", "-q", "origin", "HEAD:main")
        git(root, "checkout", "-q", "-b", "pr")
        os.makedirs(os.path.join(root, "docs/rules/roles"))
        open(os.path.join(root, NEW_FRAGMENT), "w").write("in the PR\n")
        open(os.path.join(root, QUOTED), "w").write("rule 4\n用户 2026-11-06 「一天一个记录 PR」\nplain line\n")
        open(os.path.join(root, "docs/rules/noquote.md"), "w").write("用户 「只有引文」\n2026-11-06 only a date\n")
        git(root, "add", "."), git(root, "commit", "-q", "-m", "pr")
        git(root, "push", "-q", "origin", "HEAD:refs/heads/pr")
        head = git(root, "rev-parse", "HEAD")
        for n in range(1, 40):
            git(origin, "update-ref", f"refs/pull/{n}/head", head)
        git(root, "checkout", "-q", "main")
        issues = {10: "closed", 11: "closed", 12: "open", 13: "pull request"}
        quiet = lambda body, files=(RULES,), **kw: pr("docs(rules): x（#1）", list(files), body, **kw)
        today = "2026-11-06T09:00:00Z"
        # ---- the title
        for title in ("docs(decisions): 占号（#1）", "feat(x): 占号 之后补（#1）"):
            why, _ = run(root, FakeGh({1: pr(title, ["backend/src/main/A.java"])}), [1])
            assert why and "占号" in why and "#1:" in why, why
        # ---- what it does not touch is never listed or fetched
        gh = FakeGh({1: pr("feat(x): y（#1）", ["backend/src/main/A.java", "backend/src/test/ATest.java"]), 2: pr("test: y（#2）", ["frontend/a.test.ts"]), 3: pr("ci(x): y（#3）", ["scripts/merge/mq.py"], added=[("scripts/merge/new.py", None), ("scripts/gates/gates_selftest.py", None)])}, merged=None)
        assert run(root, gh, [1, 2, 3]) == (None, "") and gh.listed == 0
        # ---- records PRs: one per calendar day (Europe/Berlin)
        rec = lambda *p, **kw: pr("docs(decisions): 记录（#1）", list(p) or [NEW_FRAGMENT], **kw)
        assert run(root, FakeGh({1: rec()}, merged=[]), [1]) == (None, "")
        why, _ = run(root, FakeGh({1: rec()}, merged=[merged(5, [FRAGMENT], today)]), [1])
        assert why and "#5 (merged)" in why and "one per calendar day" in why and "next day" in why, why
        assert run(root, FakeGh({1: rec()}, merged=[merged(5, ["docs/rules/rulings/r.md"], "2026-11-05T23:30:00Z")]), [1])[0], "23:30Z on the 5th is 00:30 Berlin on the 6th: today"
        assert run(root, FakeGh({1: rec()}, merged=[merged(5, [FRAGMENT], "2026-11-05T22:30:00Z")]), [1]) == (None, ""), "22:30Z on the 5th is 23:30 Berlin on the 5th: yesterday"
        assert run(root, FakeGh({1: rec()}, merged=[merged(5, [FRAGMENT, "docs/adr/a.md"], today), merged(6, ["backend/src/main/A.java"], today)]), [1]) == (None, ""), "a merged PR that is not all records does not count"
        assert run(root, FakeGh({1: rec()}, merged=[merged(1, [FRAGMENT], today)]), [1]) == (None, ""), "itself, merged or re-enqueued, is not another"
        both = {1: rec(), 2: rec(), 3: pr("docs(adr): x（#3）", ["docs/adr/a.md"])}
        why, _ = run(root, FakeGh(both, merged=[]), [1], queued=[2])
        assert why and "#2 (queued)" in why, why
        assert run(root, FakeGh(both, merged=[]), [1], queued=[3]) == (None, ""), "a queued PR that is not records does not count"
        assert run(root, FakeGh(both, merged=[]), [1], queued=[1]) == (None, ""), "itself in the queue"
        why, _ = run(root, FakeGh(both, merged=[]), [1, 2])
        assert why and "#2 (queued)" in why, "two records PRs in one call are two on one day"
        # ---- rule PRs
        forms = "User-Decision"
        why, _ = run(root, FakeGh({1: quiet("Closes #1\n")}, merged=[]), [1])
        assert why and "rule PR" in why and forms in why and "Incident: #<a>, #<b>" in why, why
        for path in ("CLAUDE.md", "backend/CLAUDE.md", "docs/rules/roles/dev.md", "docs/rules/playbooks/ops.md"):
            assert run(root, FakeGh({1: quiet("x", files=(path,))}, merged=[]), [1])[0], path
        assert run(root, FakeGh({1: quiet("x", files=("docs/rules/coding-core.md",))}, merged=[]), [1]) == (None, "")
        ok = lambda body, files=(RULES,), merged_=(), **kw: run(root, FakeGh({1: quiet(body, files, **kw)}, merged=list(merged_), issues=issues), [1])
        assert ok(f"User-Decision: {FRAGMENT}") == (None, ""), "a fragment on main"
        assert ok(f"User-Decision: {NEW_FRAGMENT}", files=(RULES, NEW_FRAGMENT)) == (None, ""), "a fragment in the PR"
        assert "no such fragment" in ok(f"User-Decision: {NEW_FRAGMENT}")[0], "a fragment that is neither on main nor in the PR"
        assert "no such fragment" in ok("User-Decision: docs/decisions/none.md")[0]
        r_ = ok(f"User-Decision: {QUOTED}", files=(RULES, QUOTED))
        assert r_ == (None, ""), ("a changed file with a quote and a date on one line", r_)
        assert "not under docs/decisions/ and not a file this PR changes" in ok(f"User-Decision: {QUOTED}")[0]
        assert "no line with both" in ok("User-Decision: docs/rules/noquote.md", files=(RULES, "docs/rules/noquote.md"))[0], "a quote and a date on different lines"
        for bad in ("../etc/passwd", "/etc/passwd", "docs/decisions/../../x", "", "docs/decisions/a b.md", "a\\b"):
            assert "not a repository path" in ok(f"User-Decision: {bad}")[0], bad
        assert ok("Incident: #10, #11") == (None, "") and ok("Incident: #10, #11, #10") == (None, "")
        for bad in ("#10", "#10, #10", "#10, #12", "#10, #13", "#10 and #11", "#10, #11 x", "10, 11", ""):
            assert ok(f"Incident: {bad}")[0], bad
        assert "#12 is open" in ok("Incident: #10, #12")[0] and "#13 is pull request" in ok("Incident: #10, #13")[0]
        assert ok("```\nUser-Decision: " + FRAGMENT + "\n```")[0], "a quotation in a fence is no declaration"
        assert ok(f"User-Decision: {FRAGMENT}\nIncident: #10, #12")[0], "a declared line that does not hold is refused even when another holds"
        # at most one rule PR per ISO week without a User-Decision (week of Monday 2026-11-02, Berlin)
        mon = lambda number, body="", at="2026-11-03T10:00:00Z", paths=(RULES,): merged(number, list(paths), at, body)
        why = ok("Incident: #10, #11", merged_=[mon(5)])[0]
        assert why and "#5 (merged)" in why and "ISO week" in why, why
        assert ok("Incident: #10, #11", merged_=[mon(5, f"User-Decision: {FRAGMENT}")]) == (None, ""), "a rule PR with a User-Decision does not use the week up"
        assert ok("Incident: #10, #11", merged_=[mon(5, at="2026-11-01T10:00:00Z")]) == (None, ""), "Sunday the 1st is the week before"
        assert ok("Incident: #10, #11", merged_=[mon(5, at="2026-11-01T23:30:00Z")])[0], "23:30Z on Sunday is 00:30 Berlin on Monday: this week"
        assert ok("Incident: #10, #11", merged_=[mon(5, paths=("docs/adr/a.md",))]) == (None, "")
        assert ok(f"User-Decision: {FRAGMENT}", merged_=[mon(5), mon(6)]) == (None, ""), "with a User-Decision the weekly cap does not apply"
        queued_rule = {1: quiet("Incident: #10, #11"), 2: quiet("Incident: #10, #11"), 3: quiet(f"User-Decision: {FRAGMENT}"), 4: pr("docs: y（#4）", ["docs/adr/a.md"])}
        why, _ = run(root, FakeGh(queued_rule, merged=[], issues=issues), [1], queued=[2])
        assert why and "#2 (queued)" in why, why
        assert run(root, FakeGh(queued_rule, merged=[], issues=issues), [1], queued=[3, 4]) == (None, "")
        # ---- a PR that adds a gate, guard or hook file
        gate = lambda body, added, files=None: run(root, FakeGh({1: pr("ci(gates): x（#1）", files or [p for p, _ in added], body, added=added)}, merged=[], issues=issues), [1])
        new = [("scripts/gates/new_gate.py", None)]
        why, _ = gate("Closes #1", new)
        assert why and "scripts/gates/new_gate.py" in why and "Incident" in why and "User-Decision" in why, why
        assert gate("Incident: #10, #11", new) == (None, "") and gate(f"User-Decision: {FRAGMENT}", new, files=["scripts/gates/new_gate.py", FRAGMENT]) == (None, "")
        assert gate("Closes #1", [("scripts/guards/g.py", None)])[0] and gate("Closes #1", [("scripts/hooks/h.sh", None)])[0]
        assert gate("Closes #1", [("scripts/gates/new_selftest.py", None), ("scripts/gates/x_cases.py", None), ("scripts/merge/m.py", None)]) == (None, ""), "self-tests and other directories are not check files"
        assert gate("Closes #1", [], files=["scripts/gates/old.py"]) == (None, ""), "a modified file is not an added one"
        assert gate("Closes #1", [("scripts/gates/new_name.py", "scripts/gates/old.py")]) == (None, ""), "a rename within the check directories adds no check"
        assert gate("Closes #1", [("scripts/gates/new_name.py", "scripts/ci/old.py")])[0], "a rename in from elsewhere is an added check"
        assert "not a closed issue" in gate("Incident: #10, #12", new)[0] or "#12 is open" in gate("Incident: #10, #12", new)[0]
        assert gate("Incident: #10, #11\nIncident: #10, #10", new)[0]
        assert gate("Incident: #10, #11", new) == (None, ""), "no weekly cap on check files"
        # ---- messages name the numbers and the forms; every PR is judged, all refusals are listed
        why, _ = run(root, FakeGh({1: quiet("x"), 2: pr("docs: 占号（#2）", ["docs/adr/a.md"])}, merged=[]), [1, 2])
        assert why.startswith("nothing queued") and "#1:" in why and "#2:" in why, why
        # ---- what cannot be read lets it through, said on stderr
        for label, gh_ in (("merged", FakeGh({1: rec()}, merged=None)), ("issue", FakeGh({1: quiet("Incident: #10, #11")}, merged=[], issues={})), ("state", FakeGh({1: {**rec(), "state": "MERGED"}}, merged=[]))):
            why, err = run(root, gh_, [1])
            assert why is None and "records and rules check not run for #1" in err and "queued without the check" in err, (label, why, err)
        class NoFiles(FakeGh):
            def file_stats(self, n):
                return None
        why, err = run(root, NoFiles({1: rec()}, merged=[]), [1])
        assert why is None and "not run" in err
        why, err = run(os.path.join(tmp, "not-a-repo"), FakeGh({1: quiet(f"User-Decision: {FRAGMENT}")}, merged=[]), [1])
        assert why is None and "not run" in err, (why, err)
        # the time zone data missing is a tool error, not a crash
        import unittest.mock as mock
        with mock.patch.object(mq_records, "zone", side_effect=mq_records.ToolError("no tz")):
            why, err = run(root, FakeGh({1: rec()}, merged=[]), [1])
        assert why is None and "not run" in err
    print("mq_records_selftest: ok")


if __name__ == "__main__":
    main()
