#!/usr/bin/env python3
"""Fail when a commit of the pull request changes an acceptance test without saying who may (SESSION_PLAYBOOK 附 M1 第 4 条、TEAM-69).
Acceptance tests are the files under backend/src/test/java/com/forcitis/one/acceptance/, backend/src/test/resources/acceptance/
and any frontend `__acceptance__` directory (not `acceptance` packages inside modules, e.g. the delivery.acceptance context).
A commit that changes them needs a trailer `Acceptance-Test-Author: test-engineer` or `Acceptance-Ruling: <ruling id>` (ids look
like RULING-12), read from the real trailer block of the message. A commit carrying Acceptance-Test-Author may change nothing
but acceptance tests. A merge commit counts as changing what differs from the automatic merge of its parents (git merge-tree)
plus the paths that conflicted, so a clean merge is free while resolving a conflict or taking one side wholesale is not;
an octopus merge always needs a trailer. Needs git 2.38 or newer.
Usage: acceptance_guard.py <base-ref> [head-ref]  |  acceptance_guard.py --self-test"""
import os
import re
import sys
import tempfile

from ci_git import CiError, commit_of, git, require_full_history, require_version, run, toplevel

ACCEPTANCE = re.compile(
    r"^(backend/src/test/java/com/forcitis/one/acceptance/.+|backend/src/test/resources/acceptance/.+|frontend/(?!.*node_modules/)(.+/)?__acceptance__/.+)$")
RULING_ID = re.compile(r"[A-Z][A-Z0-9-]*-\d+")


def trailer_values(sha, key, root):
    out = git("log", "-1", f"--format=%(trailers:key={key},valueonly,unfold)", sha, cwd=root)
    return [line.strip() for line in out.splitlines() if line.strip()]


def parents_of(sha, root):
    return git("rev-list", "--parents", "-n", "1", sha, cwd=root).split()[1:]


def changed_files(sha, parents, root):
    if len(parents) < 2:
        return git("diff-tree", "--no-commit-id", "--name-only", "-r", "--root", "--no-renames", sha, cwd=root).splitlines()
    if len(parents) > 2:
        return git("diff", "--name-only", "--no-renames", parents[0], sha, cwd=root).splitlines()
    result = run("merge-tree", "--write-tree", "--name-only", *parents, cwd=root)
    if result.returncode not in (0, 1):
        raise CiError(f"git merge-tree of the parents of {sha[:10]} failed: {result.stderr.strip()}")
    lines = result.stdout.split("\n")
    conflicted = []
    for line in lines[1:]:
        if not line:
            break
        conflicted.append(line)
    resolved = git("diff", "--name-only", "--no-renames", lines[0], sha, cwd=root).splitlines()
    return sorted(set(resolved) | set(conflicted))


def violations(base, head, root):
    found = []
    base_sha, head_sha = commit_of(base, root), commit_of(head, root)
    for sha in git("rev-list", "--reverse", f"{base_sha}..{head_sha}", cwd=root).split():
        parents = parents_of(sha, root)
        files = changed_files(sha, parents, root)
        acceptance = [f for f in files if ACCEPTANCE.match(f)]
        elsewhere = [f for f in files if not ACCEPTANCE.match(f)]
        authored = "test-engineer" in trailer_values(sha, "Acceptance-Test-Author", root)
        ruled = any(RULING_ID.fullmatch(v) for v in trailer_values(sha, "Acceptance-Ruling", root))
        subject = git("log", "-1", "--format=%s", sha, cwd=root).strip()
        if len(parents) > 2 and not (authored or ruled):
            found.append((sha, subject, "octopus merges need an Acceptance-Test-Author or Acceptance-Ruling trailer", files))
        elif authored and elsewhere:
            found.append((sha, subject, "Acceptance-Test-Author commits may only change acceptance tests", elsewhere))
        elif acceptance and not (authored or ruled):
            found.append((sha, subject, "changes acceptance tests without a valid Acceptance-Test-Author or Acceptance-Ruling trailer", acceptance))
    return found


def commit(repo, files, message, remove=()):
    for path, text in files.items():
        full = os.path.join(repo, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w") as f:
            f.write(text)
    for path in remove:
        git("rm", "-q", path, cwd=repo)
    git("add", "-A", cwd=repo)
    git("commit", "-q", "-m", message, cwd=repo)


def new_repo(root):
    repo = tempfile.mkdtemp(dir=root)
    git("init", "-q", "-b", "main", cwd=repo)
    git("config", "user.email", "ci@example.invalid", cwd=repo)
    git("config", "user.name", "ci", cwd=repo)
    return repo


AUTHOR = "\n\nAcceptance-Test-Author: test-engineer"
BACKEND = "backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingAcceptanceTest.java"
OTHER_BACKEND = "backend/src/test/java/com/forcitis/one/acceptance/platform/identity/LoginAcceptanceTest.java"
FRONTEND = "frontend/apps/h5/src/__acceptance__/login.spec.ts"


def self_test():
    with tempfile.TemporaryDirectory() as root:
        trailers(root)
        merges(root)
    print("acceptance_guard self-test: ok")
    return 0


def trailers(root):
    repo = new_repo(root)
    commit(repo, {"README.md": "x\n", BACKEND: "v0\n"}, "base" + AUTHOR)
    git("checkout", "-q", "-b", "feature", cwd=repo)

    def found():
        return [(message, files) for _, message, _, files in violations("main", "HEAD", repo)]

    def reasons():
        return [reason for _, _, reason, _ in violations("main", "HEAD", repo)]

    commit(repo, {"backend/src/main/java/A.java": "a\n", "backend/src/test/java/com/forcitis/one/UnitTest.java": "a\n"}, "implementation only")
    commit(repo, {
        "frontend/node_modules/x/__acceptance__/y.js": "a\n",
        "backend/src/test/java/com/forcitis/one/acceptance_notes.txt": "a\n",
        "backend/src/test/java/com/forcitis/one/delivery/acceptance/AcceptanceRecordTest.java": "a\n",
        "frontend/apps/h5/src/acceptance/module.spec.ts": "a\n",
    }, "lookalikes")
    assert found() == [], "files that only look like acceptance tests must pass"

    commit(repo, {BACKEND: "v1\n"}, "backend acceptance without trailer")
    assert found() == [("backend acceptance without trailer", [BACKEND])]
    git("reset", "-q", "--hard", "HEAD~1", cwd=repo)

    for message, ok in [
        ("empty ruling\n\nAcceptance-Ruling: ", False),
        ("free-text ruling\n\nAcceptance-Ruling: yes", False),
        ("lower-case ruling\n\nAcceptance-Ruling: ruling-12", False),
        ("other author\n\nAcceptance-Test-Author: someone-else", False),
        ("trailer not in the last paragraph\n\nAcceptance-Test-Author: test-engineer\n\nclosing words", False),
        ("ruling\n\nAcceptance-Ruling: RULING-12", True),
        ("ruling with other trailers\n\nAcceptance-Ruling: B3-R7-12\nCo-Authored-By: x <x@example.invalid>", True),
        ("test engineer" + AUTHOR, True),
    ]:
        commit(repo, {BACKEND: message}, message)
        assert (found() == []) == ok, f"{message!r} should {'pass' if ok else 'fail'}: {found()}"
        git("reset", "-q", "--hard", "HEAD~1", cwd=repo)

    commit(repo, {FRONTEND: "a\n", "backend/src/test/resources/acceptance/numbering/case.json": "{}\n"}, "frontend and resources" + AUTHOR)
    assert found() == [], "frontend and resource acceptance files with the author trailer pass"
    git("reset", "-q", "--hard", "HEAD~1", cwd=repo)

    commit(repo, {BACKEND: "v2\n", "backend/src/main/java/B.java": "b\n"}, "author touching elsewhere" + AUTHOR)
    assert found() == [("author touching elsewhere", ["backend/src/main/java/B.java"])] and "may only change" in reasons()[0]
    git("reset", "-q", "--hard", "HEAD~1", cwd=repo)

    commit(repo, {}, "deleting an acceptance test counts\n\nAcceptance-Test-Author: someone-else", remove=[BACKEND])
    assert [m for m, _ in found()] == ["deleting an acceptance test counts"]
    git("reset", "-q", "--hard", "HEAD~1", cwd=repo)

    commit(repo, {"backend/src/test/java/com/forcitis/one/Moved.java": "v0\n"}, "moving an acceptance test out of the directory", remove=[BACKEND])
    assert [m for m, _ in found()] == ["moving an acceptance test out of the directory"]
    assert found()[0][1] == [BACKEND]
    git("reset", "-q", "--hard", "HEAD~1", cwd=repo)

    try:
        violations("no-such-ref", "HEAD", repo)
        raise AssertionError("an unknown base must fail")
    except CiError:
        pass


def merges(root):
    body = "".join(f"line{i}\n" for i in range(30))

    def setup(content):
        repo = new_repo(root)
        commit(repo, {"README.md": "x\n", BACKEND: content}, "base" + AUTHOR)
        return repo, git("rev-parse", "HEAD", cwd=repo).strip()

    def flagged(repo, base):
        return [(m, files) for _, m, _, files in violations(base, "HEAD", repo)]

    def merge_commit(repo, branch, message, resolve=None):
        run("merge", "--no-ff", "--no-commit", branch, cwd=repo)
        if resolve:
            resolve()
        git("add", "-A", cwd=repo)
        git("commit", "-q", "-m", message, cwd=repo)

    repo, base = setup(body)
    git("checkout", "-q", "-b", "feature", cwd=repo)
    commit(repo, {BACKEND: body.replace("line1\n", "feature1\n")}, "feature side" + AUTHOR)
    git("checkout", "-q", "main", cwd=repo)
    commit(repo, {BACKEND: body.replace("line28\n", "main28\n")}, "main side" + AUTHOR)
    git("checkout", "-q", "--detach", "main", cwd=repo)
    merge_commit(repo, "feature", "synthetic merge of the same file, different hunks")
    assert flagged(repo, base) == [], "a clean automatic merge of one acceptance file is not a manual change"
    git("checkout", "-q", "feature", cwd=repo)
    merge_commit(repo, "main", "Merge main into feature")
    assert flagged(repo, base) == [], "merging main into the branch is free too"

    repo, base = setup("v0\n")
    git("checkout", "-q", "-b", "feature", cwd=repo)
    commit(repo, {"backend/src/main/java/S.java": "s\n"}, "implementation")
    git("checkout", "-q", "main", cwd=repo)
    commit(repo, {BACKEND: "v1\n"}, "ruled\n\nAcceptance-Ruling: RULING-3")
    git("checkout", "-q", "feature", cwd=repo)
    merge_commit(repo, "main", "keep the old acceptance test", lambda: git("checkout", "HEAD", "--", BACKEND, cwd=repo))
    assert flagged(repo, base) == [("keep the old acceptance test", [BACKEND])], "restoring an old version while merging needs a trailer"

    repo, base = setup("v0\n")
    git("checkout", "-q", "-b", "feature", cwd=repo)
    commit(repo, {BACKEND: "feature\n"}, "feature side" + AUTHOR)
    git("checkout", "-q", "main", cwd=repo)
    commit(repo, {BACKEND: "main\n"}, "main side" + AUTHOR)
    git("checkout", "-q", "feature", cwd=repo)
    merge_commit(repo, "main", "take ours", lambda: git("checkout", "--ours", "--", BACKEND, cwd=repo))
    assert flagged(repo, base) == [("take ours", [BACKEND])], "taking one side of a conflict needs a trailer"
    git("reset", "-q", "--hard", "HEAD~1", cwd=repo)
    merge_commit(repo, "main", "resolve", lambda: open(os.path.join(repo, BACKEND), "w").write("resolution\n"))
    assert flagged(repo, base) == [("resolve", [BACKEND])], "resolving a conflict in an acceptance test needs a trailer"
    git("commit", "-q", "--amend", "-m", "resolve\n\nAcceptance-Ruling: RULING-7", cwd=repo)
    assert flagged(repo, base) == [], "a valid trailer lets the resolution through"
    git("reset", "-q", "--hard", "HEAD~1", cwd=repo)
    merge_commit(repo, "main", "resolve by the test engineer", lambda: open(os.path.join(repo, BACKEND), "w").write("resolution\n"))
    git("commit", "-q", "--amend", "-m", "resolve by the test engineer" + AUTHOR, cwd=repo)
    assert flagged(repo, base) == []

    repo, base = setup("v0\n")
    for name in ("a", "b"):
        git("checkout", "-q", "-b", name, "main", cwd=repo)
        commit(repo, {f"{name}.txt": "x\n"}, name)
    git("checkout", "-q", "main", cwd=repo)
    run("merge", "--no-ff", "--no-commit", "a", "b", cwd=repo)
    git("commit", "-q", "-m", "octopus", cwd=repo)
    assert flagged(repo, base) == [("octopus", ["a.txt", "b.txt"])], "an octopus merge needs a trailer"
    git("commit", "-q", "--amend", "-m", "octopus\n\nAcceptance-Ruling: RULING-1", cwd=repo)
    assert flagged(repo, base) == []


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    if len(sys.argv) not in (2, 3):
        print(__doc__, file=sys.stderr)
        return 2
    try:
        root = toplevel()
        require_version((2, 38), root)
        require_full_history(root)
        found = violations(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "HEAD", root)
    except CiError as e:
        print(f"::error title=Acceptance tests::{e}")
        return 1
    for sha, subject, reason, files in found:
        print(f"::error title=Acceptance tests::{sha[:10]} {subject}: {reason}: {', '.join(files)}")
    print(f"acceptance_guard: {len(found)} violating commit(s)")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
