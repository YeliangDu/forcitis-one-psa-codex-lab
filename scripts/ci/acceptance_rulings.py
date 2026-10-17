#!/usr/bin/env python3
"""Check that the Acceptance-Ruling a commit names is a real ruling (SESSION_PLAYBOOK §6.1 item 8; scripts/ci/acceptance_guard.py only checks that a trailer of the right shape is there).

Acceptance tests may be changed by the test engineer, or by a commit that carries `Acceptance-Ruling: RULING-<id>`: a review (not the implementing session) decided the test was wrong. The ruling must exist in the
repository record docs/modernization/rulings/ACCEPTANCE_RULINGS.yaml, **as of the pull request's base** (a record added in the same pull request is the implementing session ruling for itself). An entry is
  - id: RULING-12
    date: 2026-10-17
    ruled_by: one-coordinator        # who decided: the review, the coordinator, the user; never the session that wants the change
    implementer: one-1a-next-5       # the session whose implementation the test blocked
    files: [backend/src/test/java/com/forcitis/one/acceptance/...]   # what the ruling lets change (at least one path or directory prefix)
    reason: one line, what was wrong with the test
    ref: where the decision is written down (intent file, review record, ADR)        # optional
Per commit of base..head that carries Acceptance-Ruling trailers, a trailer is
  unrecorded  the id is not in the base's record
  invalid     the entry lacks date / ruled_by / implementer / files / reason, or ruled_by equals implementer (or is a session name starting with `self`), or the date is not a date
  out of scope  the commit changes an acceptance test that none of the entry's `files` covers (path equal to, or under, an entry)
Report mode (default): prints the findings as warnings, exit 0; `--enforce` exits 1 on any. Existing rulings in the history (see `--history`) have no record yet: the report lists them so the coordinator can write the entries; enforcement
is switched on (ENFORCE = True) after that.
Usage: acceptance_rulings.py <base-ref> [head-ref] [--enforce]  |  acceptance_rulings.py --history [rev-range]  |  acceptance_rulings.py --self-test"""
import datetime
import os
import re
import sys
import tempfile

from acceptance_guard import ACCEPTANCE, RULING_ID, changed_files, parents_of, trailer_values
from ci_git import CiError, commit_of, git, git_ok, require_full_history, run, toplevel

RECORD = "docs/modernization/rulings/ACCEPTANCE_RULINGS.yaml"
ENFORCE = False
FIELDS = ("id", "date", "ruled_by", "implementer", "files", "reason")


def load_record(text):
    """{id: entry} from the record's text; an entry that is not a mapping or has no id is dropped here and found by the invalid check (kept under its position as `?<n>`)."""
    import yaml
    data = yaml.safe_load(text) if text and text.strip() else None
    entries = (data or {}).get("rulings") or []
    out = {}
    for i, e in enumerate(entries):
        if isinstance(e, dict) and e.get("id"):
            out[str(e["id"])] = e
        else:
            out[f"?{i}"] = e if isinstance(e, dict) else {}
    return out


def record_at(ref, root):
    result = run("show", f"{ref}:{RECORD}", cwd=root)
    return load_record(result.stdout) if result.returncode == 0 else {}


def problems_of(entry):
    """Why an entry is not a ruling by a review: a list of short reasons, empty when it is one."""
    why = [f"{f} missing" for f in FIELDS if f != "id" and not entry.get(f)]
    ruled, implementer = str(entry.get("ruled_by") or "").strip(), str(entry.get("implementer") or "").strip()
    if ruled and implementer and ruled == implementer:
        why.append("ruled_by is the implementing session itself")
    if ruled.lower().startswith("self"):
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
        for rid in ids:
            entry = record.get(rid)
            if entry is None:
                found.append((sha, subject, rid, "unrecorded", f"not in {RECORD} at the base of this pull request (a ruling must be recorded, and merged, before the commit that relies on it)"))
                continue
            bad = problems_of(entry)
            if bad:
                found.append((sha, subject, rid, "invalid", "; ".join(bad)))
            uncovered = [f for f in touched if not covers(entry, f)]
            if uncovered and not bad:
                found.append((sha, subject, rid, "out of scope", f"changes {', '.join(uncovered[:5])} which the ruling's files do not cover"))
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
    good = ("rulings:\n  - id: RULING-1\n    date: 2026-10-17\n    ruled_by: one-coordinator\n    implementer: one-1a-next-5\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform/FooIT.java]\n    reason: the test asserted the old name\n"
            "  - id: RULING-2\n    date: 2026-10-17\n    ruled_by: one-1a-next-5\n    implementer: one-1a-next-5\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform]\n    reason: self ruled\n"
            "  - id: RULING-3\n    date: not-a-date\n    ruled_by: one-coordinator\n    implementer: x\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform]\n    reason: r\n"
            "  - id: RULING-4\n    date: 2026-10-17\n    ruled_by: one-coordinator\n    implementer: x\n    reason: no files\n"
            "  - id: RULING-5\n    date: 2026-10-17\n    ruled_by: self-review\n    implementer: x\n    files: [backend/src/test/java/com/forcitis/one/acceptance]\n    reason: r\n"
            "  - id: RULING-6\n    date: 2026-10-17\n    ruled_by: 用户\n    implementer: x\n    files: [backend/src/test/java/com/forcitis/one/acceptance/platform]\n    reason: r\n")
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
        import subprocess
        me = os.path.abspath(__file__)
        git("checkout", "-q", "-B", "cli", "base-with-record", cwd=repo)
        commit(repo, {ACC: "cli\n"}, "fix" + trailer("RULING-9"))
        report = subprocess.run([sys.executable, me, "base-with-record", "cli"], cwd=repo, capture_output=True, text=True)
        assert report.returncode == 0 and "::warning title=Acceptance rulings::" in report.stdout and "RULING-9 unrecorded" in report.stdout and "(report only)" in report.stdout, report.stdout
        enforced = subprocess.run([sys.executable, me, "base-with-record", "cli", "--enforce"], cwd=repo, capture_output=True, text=True)
        assert enforced.returncode == 1 and "::error title=Acceptance rulings::" in enforced.stdout, enforced.stdout
        clean = subprocess.run([sys.executable, me, "base-with-record", "base-with-record", "--enforce"], cwd=repo, capture_output=True, text=True)
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
    enforce = ENFORCE or "--enforce" in args
    args = [a for a in args if a != "--enforce"]
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
