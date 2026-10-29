#!/usr/bin/env python3
"""Check that the Acceptance-Ruling a commit names is a real ruling (SESSION_PLAYBOOK 附 M1 第 4 条、TEAM-69; scripts/guards/acceptance_guard.py only checks that a trailer of the right shape is there).

Acceptance tests may be changed by the test engineer, or by a commit that carries `Acceptance-Ruling: RULING-<id>`: a review (not the implementing session) decided the test was wrong. The ruling must exist in the
repository record docs/rules/rulings/acceptance-rulings.yaml, **as of the pull request's base** (a record added in the same pull request is the implementing session ruling for itself). An entry is
  - id: RULING-12
    date: 2026-10-17
    ruled_by: one-coordinator        # who decided: the review, the coordinator, the user; never the session that wants the change
    implementer: one-1a-next-5       # the session whose implementation the test blocked
    files: [backend/src/test/java/com/forcitis/one/acceptance/...]   # what the ruling lets change (at least one path or directory prefix)
    reason: one line, what was wrong with the test
    ref: where the decision is written down (intent file, review record, ADR, comment link)
Per commit of base..head that carries Acceptance-Ruling trailers, a trailer is
  unrecorded  the id is not in the base's record
  invalid     the entry lacks date / ruled_by / implementer / files / reason / ref, the id is listed twice, or ruled_by equals implementer (compared by the session name each starts with, case-insensitively) or starts with `self`, or the date is not a date
  reused      the id was already relied on by an earlier commit of the base (a ruling lets one pull request through, any number of its commits)
  out of scope  the commit changes an acceptance test that none of the ruling entries it names covers together (`files`: path equal to, or under, an entry)
Enforcing (ENFORCE = True): any finding fails; `--report` prints them as warnings and exits 0. Only the commits of base..head are judged, so rulings in the history before this check reached main (see `--history`; the coordinator decided on 2026-10-17 not to back-fill them) are not checked.
Usage: acceptance_rulings.py <base-ref> [head-ref] [--report]  |  acceptance_rulings.py --history [rev-range]  |  acceptance_rulings.py --self-test"""
import datetime
import os
import re
import subprocess
import sys
import tempfile

from acceptance_guard import ACCEPTANCE, RULING_ID, changed_files, parents_of, trailer_values
from ci_git import CiError, commit_of, git, git_ok, require_full_history, run, toplevel

RECORD = "docs/rules/rulings/acceptance-rulings.yaml"
ENFORCE = True
FIELDS = ("id", "date", "ruled_by", "implementer", "files", "reason", "ref")


def load_record(text):
    """{id: entry} from the record's text; an entry that is not a mapping or has no id is dropped here and found by the invalid check (kept under its position as `?<n>`)."""
    import yaml
    data = yaml.safe_load(text) if text and text.strip() else None
    entries = (data or {}).get("rulings") or []
    out = {}
    for i, e in enumerate(entries):
        if isinstance(e, dict) and e.get("id"):
            rid = str(e["id"])
            if rid in out:
                out[rid]["_duplicates"] = out[rid].get("_duplicates", 0) + 1
            else:
                out[rid] = e
        else:
            out[f"?{i}"] = e if isinstance(e, dict) else {}
    return out


def record_at(ref, root):
    result = run("show", f"{ref}:{RECORD}", cwd=root)
    return load_record(result.stdout) if result.returncode == 0 else {}


def session_of(value):
    """The session name a free-text field starts with, lowercase (`One-X（追认）` and `[one-x]` both name one-x)."""
    match = re.search(r"[\w][\w-]*", str(value or ""))
    return match.group(0).casefold() if match else ""


def problems_of(entry):
    """Why an entry is not a ruling by a review: a list of short reasons, empty when it is one."""
    why = [f"{f} missing" for f in FIELDS if f != "id" and not entry.get(f)]
    if entry.get("_duplicates"):
        why.append("the id appears more than once in the record")
    ruled, implementer = session_of(entry.get("ruled_by")), session_of(entry.get("implementer"))
    if ruled and implementer and ruled == implementer:
        why.append("ruled_by is the implementing session itself")
    if ruled.startswith("self"):
        why.append("ruled_by names the author itself")
    try:
        if entry.get("date"):
            datetime.date.fromisoformat(str(entry["date"]))
    except ValueError:
        why.append(f"date is not a date: {entry.get('date')}")
    files = entry.get("files")
    if files is not None and not (isinstance(files, list) and files and all(isinstance(f, str) and f.strip() for f in files)):
        why.append("files must be a non-empty list of paths")
    return why


def covers(entry, path):
    files = entry.get("files") if isinstance(entry.get("files"), list) else []
    return any(isinstance(f, str) and f.strip() and (path == f.strip().rstrip("/") or path.startswith(f.strip().rstrip("/") + "/")) for f in files)


def used_before(base_sha, rid, root):
    """The first commit reachable from the base that already carries the trailer for rid, or ''."""
    for sha in git("rev-list", "-i", "--grep=Acceptance-Ruling", base_sha, cwd=root).split():
        if rid in trailer_values(sha, "Acceptance-Ruling", root):
            return sha
    return ""


def findings(base, head, root):
    """[(sha, subject, id, kind, detail)]: every Acceptance-Ruling trailer of base..head that is not backed by a ruling of the base's record."""
    base_sha, head_sha = commit_of(base, root), commit_of(head, root)
    record = record_at(base_sha, root)
    found = []
    for sha in git("rev-list", "--reverse", f"{base_sha}..{head_sha}", cwd=root).split():
        ids = [v for v in trailer_values(sha, "Acceptance-Ruling", root) if RULING_ID.fullmatch(v)]
        if not ids:
            continue
        subject = git("log", "-1", "--format=%s", sha, cwd=root).strip()
        touched = [f for f in changed_files(sha, parents_of(sha, root), root) if ACCEPTANCE.match(f)]
        valid = []
        for rid in ids:
            entry = record.get(rid)
            if entry is None:
                found.append((sha, subject, rid, "unrecorded", f"not in {RECORD} at the base of this pull request (a ruling must be recorded, and merged, before the commit that relies on it)"))
                continue
            bad = problems_of(entry)
            if bad:
                found.append((sha, subject, rid, "invalid", "; ".join(bad)))
                continue
            earlier = used_before(base_sha, rid, root)
            if earlier:
                found.append((sha, subject, rid, "reused", f"already relied on by {earlier[:10]} on the base: a ruling lets one change through once, ask for a new one"))
                continue
            valid.append((rid, entry))
        uncovered = [f for f in touched if valid and not any(covers(e, f) for _, e in valid)]
        if uncovered:
            found.append((sha, subject, "+".join(rid for rid, _ in valid), "out of scope", f"changes {', '.join(uncovered[:5])} which the ruling's files do not cover"))
    return found


def history(rev_range, root):
    """{id: [(sha, subject)]} for every commit of the range that carries an Acceptance-Ruling trailer, with whether the current record has it: for writing the record's first entries."""
    out = {}
    for sha in git("rev-list", "--reverse", rev_range, cwd=root).split():
        for v in trailer_values(sha, "Acceptance-Ruling", root):
            if RULING_ID.fullmatch(v):
                out.setdefault(v, []).append((sha, git("log", "-1", "--format=%s", sha, cwd=root).strip()))
    return out


def new_repo(root):
    repo = tempfile.mkdtemp(dir=root)
    git("init", "-q", "-b", "main", cwd=repo)
    git("config", "user.email", "ci@example.invalid", cwd=repo)
    git("config", "user.name", "ci", cwd=repo)
    git("config", "commit.gpgsign", "false", cwd=repo)
    return repo


def commit(repo, files, message):
    for path, text in files.items():
        full = os.path.join(repo, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w") as f:
            f.write(text)
    git("add", "-A", cwd=repo)
    git("commit", "-q", "-m", message, cwd=repo)


def self_test():
    ACC = "backend/src/test/java/com/forcitis/one/acceptance/platform/FooIT.java"
    OTHER = "backend/src/test/java/com/forcitis/one/acceptance/platform/BarIT.java"
    good = ("rulings:\n  - id: RULING-1\n    date: 2026-10-17\n    ruled_by: one-coordinator\n    implementer: one-1a-next-5\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform/FooIT.java]\n    reason: the test asserted the old name\n    ref: r\n"
            "  - id: RULING-2\n    date: 2026-10-17\n    ruled_by: one-1a-next-5\n    implementer: one-1a-next-5\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform]\n    reason: self ruled\n    ref: r\n"
            "  - id: RULING-3\n    date: not-a-date\n    ruled_by: one-coordinator\n    implementer: x\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform]\n    reason: r\n    ref: r\n"
            "  - id: RULING-4\n    date: 2026-10-17\n    ruled_by: one-coordinator\n    implementer: x\n    reason: no files\n"
            "  - id: RULING-5\n    date: 2026-10-17\n    ruled_by: self-review\n    implementer: x\n    files: [backend/src/test/java/com/forcitis/one/acceptance]\n    reason: r\n    ref: r\n"
            "  - id: RULING-7\n    date: 2026-10-17\n    ruled_by: one-coordinator\n    implementer: x\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform]\n    reason: no ref\n"
            "  - id: RULING-8\n    date: 2026-10-17\n    ruled_by: one-coordinator\n    implementer: x\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform/FooIT.java]\n    reason: r\n    ref: r\n"
            "  - id: RULING-8\n    date: 2026-10-17\n    ruled_by: one-coordinator\n    implementer: x\n    files: [backend/src/test/java/com/forcitis/one/acceptance]\n    reason: widened\n    ref: r\n"
            "  - id: RULING-10\n    date: 2026-10-17\n    ruled_by: One-X（追认）\n    implementer: [one-x]\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform]\n    reason: r\n    ref: r\n"
            "  - id: RULING-11\n    date: 2026-10-17\n    ruled_by: one-coordinator\n    implementer: x\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform/Foo]\n    reason: a sibling prefix, not FooIT\n    ref: r\n"
            "  - id: RULING-12\n    date: 2026-10-17\n    ruled_by: one-coordinator\n    implementer: x\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform/BarIT.java]\n    reason: r\n    ref: r\n"
            "  - id: RULING-6\n    date: 2026-10-17\n    ruled_by: 用户\n    implementer: x\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform]\n    reason: r\n    ref: r\n")
    with tempfile.TemporaryDirectory() as tmp:
        repo = new_repo(tmp)
        commit(repo, {"README.md": "x\n", ACC: "v1\n", OTHER: "v1\n"}, "base without a record")
        git("branch", "no-record", cwd=repo)
        commit(repo, {RECORD: good}, "record the rulings")
        git("tag", "base-with-record", cwd=repo)

        def run_case(files, message, base="base-with-record"):
            git("checkout", "-q", "-B", "work", base, cwd=repo)
            commit(repo, files, message)
            return [(rid, kind) for _, _, rid, kind, _ in findings(base, "work", repo)]
        trailer = lambda rid: f"\n\nAcceptance-Ruling: {rid}\n"
        assert run_case({ACC: "v2\n"}, "fix" + trailer("RULING-1")) == [], "a recorded ruling by a review that covers the file"
        assert run_case({ACC: "v3\n"}, "fix" + trailer("RULING-9")) == [("RULING-9", "unrecorded")]
        assert run_case({ACC: "v4\n"}, "fix" + trailer("RULING-2")) == [("RULING-2", "invalid")], "the implementer ruling for itself"
        assert run_case({ACC: "v5\n"}, "fix" + trailer("RULING-3")) == [("RULING-3", "invalid")], "a date that is not a date"
        assert run_case({ACC: "v6\n"}, "fix" + trailer("RULING-4")) == [("RULING-4", "invalid")], "no files"
        assert run_case({ACC: "v7\n"}, "fix" + trailer("RULING-5")) == [("RULING-5", "invalid")], "ruled_by names the author itself"
        assert run_case({ACC: "v8\n"}, "fix" + trailer("RULING-6")) == [], "ruled by the user: fine"
        assert run_case({OTHER: "v9\n"}, "fix" + trailer("RULING-1")) == [("RULING-1", "out of scope")], "the ruling covers FooIT, not BarIT"
        assert run_case({ACC: "v10\n", OTHER: "v10\n"}, "both" + trailer("RULING-6")) == [], "a directory prefix covers both"
        assert run_case({"backend/src/main/java/A.java": "a\n"}, "no acceptance change, no ruling") == []
        assert run_case({ACC: "v11\n"}, "no trailer at all") == [], "a commit without the trailer is acceptance_guard's business, not this check's"
        assert run_case({ACC: "v12\n"}, "fix" + trailer("not-an-id")) == [], "a trailer that is not a ruling id is acceptance_guard's business"
        assert run_case({ACC: "v15\n"}, "fix" + trailer("RULING-7")) == [("RULING-7", "invalid")], "no ref"
        assert run_case({ACC: "v16\n"}, "fix" + trailer("RULING-8")) == [("RULING-8", "invalid")], "an id listed twice"
        assert run_case({ACC: "v17\n"}, "fix" + trailer("RULING-10")) == [("RULING-10", "invalid")], "the same session named with different case, an annotation and a list"
        assert run_case({ACC: "v18\n"}, "fix" + trailer("RULING-11")) == [("RULING-11", "out of scope")], "a prefix is a path prefix, not a string prefix"
        assert run_case({ACC: "v19\n", OTHER: "v19\n"}, "both\n\nAcceptance-Ruling: RULING-1\nAcceptance-Ruling: RULING-12\n") == [], "two rulings cover together"
        assert run_case({"backend/src/main/java/B.java": "b\n"}, "fix" + trailer("RULING-1")) == [], "only acceptance tests are judged against the files"
        # a ruling lets one change through once
        git("checkout", "-q", "-B", "used", "base-with-record", cwd=repo)
        commit(repo, {ACC: "used\n"}, "first" + trailer("RULING-6"))
        git("tag", "-f", "base-used", cwd=repo)
        assert run_case({ACC: "again\n"}, "second" + trailer("RULING-6"), base="base-used") == [("RULING-6", "reused")]
        assert run_case({ACC: "v20\n"}, "fix" + trailer("RULING-1"), base="base-used") == [], "RULING-6 used earlier does not make RULING-1 used"
        git("checkout", "-q", "-B", "prefixed", "base-with-record", cwd=repo)
        commit(repo, {ACC: "p\n"}, "fix" + trailer("RULING-12"))
        git("tag", "-f", "base-prefixed", cwd=repo)
        assert run_case({ACC: "v21\n"}, "fix" + trailer("RULING-1"), base="base-prefixed") == [], "RULING-12 used earlier does not make RULING-1 used"
        # the earlier use may be a merge commit and its trailer key may be spelled in another case
        git("checkout", "-q", "-B", "spelled", "base-with-record", cwd=repo)
        commit(repo, {ACC: "s\n"}, "first\n\nacceptance-ruling:   RULING-12\n")
        git("tag", "-f", "base-spelled", cwd=repo)
        assert run_case({ACC: "v22\n"}, "second" + trailer("RULING-12"), base="base-spelled") == [("RULING-12", "reused")]
        # a merge that resolves a conflict inside a test the ruling does not cover
        git("checkout", "-q", "-B", "mainline", "base-with-record", cwd=repo)
        commit(repo, {OTHER: "mainline\n"}, "mainline change")
        git("checkout", "-q", "-B", "merging", "base-with-record", cwd=repo)
        commit(repo, {OTHER: "side\n"}, "side change")
        merged = subprocess.run(["git", "merge", "--no-commit", "--no-ff", "mainline"], cwd=repo, capture_output=True, text=True)
        assert merged.returncode != 0, "the two branches change BarIT differently"
        commit(repo, {OTHER: "resolved\n"}, "Merge mainline" + trailer("RULING-1"))
        assert [(rid, kind) for _, _, rid, kind, _ in findings("base-with-record", "merging", repo)] == [("RULING-1", "out of scope")]
        # the record is read at the base: a ruling recorded in the same pull request is the implementer ruling for itself
        git("checkout", "-q", "-B", "same-pr", "no-record", cwd=repo)
        commit(repo, {RECORD: good}, "record in the same pull request")
        commit(repo, {ACC: "v13\n"}, "fix" + trailer("RULING-1"))
        assert [(rid, kind) for _, _, rid, kind, _ in findings("no-record", "same-pr", repo)] == [("RULING-1", "unrecorded")]
        # two trailers, one bad
        git("checkout", "-q", "-B", "two", "base-with-record", cwd=repo)
        commit(repo, {ACC: "v14\n"}, "fix\n\nAcceptance-Ruling: RULING-1\nAcceptance-Ruling: RULING-9\n")
        assert [(rid, kind) for _, _, rid, kind, _ in findings("base-with-record", "two", repo)] == [("RULING-9", "unrecorded")]
        assert history("no-record..same-pr", repo) == {"RULING-1": [(git("rev-parse", "same-pr", cwd=repo).strip(), "fix")]}
        # the command line: report mode warns and exits 0, --enforce errors and exits 1, a clean pull request exits 0 either way
        me = os.path.abspath(__file__)
        git("checkout", "-q", "-B", "cli", "base-with-record", cwd=repo)
        commit(repo, {ACC: "cli\n"}, "fix" + trailer("RULING-9"))
        report = subprocess.run([sys.executable, me, "base-with-record", "cli", "--report"], cwd=repo, capture_output=True, text=True)
        assert report.returncode == 0 and "::warning title=Acceptance rulings::" in report.stdout and "RULING-9 unrecorded" in report.stdout and "(report only)" in report.stdout, report.stdout
        enforced = subprocess.run([sys.executable, me, "base-with-record", "cli"], cwd=repo, capture_output=True, text=True)
        assert enforced.returncode == 1 and "::error title=Acceptance rulings::" in enforced.stdout, enforced.stdout
        clean = subprocess.run([sys.executable, me, "base-with-record", "base-with-record"], cwd=repo, capture_output=True, text=True)
        assert clean.returncode == 0 and "0 finding(s)" in clean.stdout, clean.stdout
        listing = subprocess.run([sys.executable, me, "--history", "base-with-record..cli"], cwd=repo, capture_output=True, text=True)
        assert listing.returncode == 0 and "RULING-9: NOT RECORDED" in listing.stdout, listing.stdout
        assert load_record("") == {} and load_record("rulings: []\n") == {} and list(load_record("rulings:\n  - not a mapping\n")) == ["?0"]
        record = load_record(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", RECORD), encoding="utf-8").read())
        assert not [i for i, e in record.items() if problems_of(e)], "the repository's own record is well formed"
    print("acceptance_rulings self-test: ok")


def main():
    args = sys.argv[1:]
    if args == ["--self-test"]:
        return self_test()
    enforce = ENFORCE and "--report" not in args or "--enforce" in args
    args = [a for a in args if a not in ("--enforce", "--report")]
    try:
        root = toplevel()
        if args[:1] == ["--history"]:
            record = record_at("HEAD", root)
            for rid, commits in sorted(history(args[1] if len(args) > 1 else "HEAD", root).items()):
                print(f"{rid}: {'recorded' if rid in record else 'NOT RECORDED'}; {len(commits)} commit(s): {', '.join(sha[:10] + ' ' + subject[:50] for sha, subject in commits[:3])}")
            return 0
        if len(args) not in (1, 2):
            print(__doc__, file=sys.stderr)
            return 2
        require_full_history(root)
        found = findings(args[0], args[1] if len(args) > 1 else "HEAD", root)
    except CiError as e:
        print(f"::error title=Acceptance rulings::{e}")
        return 1
    level = "error" if enforce else "warning"
    for sha, subject, rid, kind, detail in found:
        print(f"::{level} title=Acceptance rulings::{sha[:10]} {subject}: {rid} {kind}: {detail}")
    print(f"acceptance_rulings: {len(found)} finding(s)" + ("" if enforce else " (report only)"))
    return 1 if (found and enforce) else 0


if __name__ == "__main__":
    sys.exit(main())
