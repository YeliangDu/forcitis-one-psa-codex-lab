#!/usr/bin/env python3
"""Fail when a commit of the pull request changes an acceptance test without saying who may (SESSION_PLAYBOOK §6.1 item 8).
Acceptance tests are the files under an `acceptance` directory in backend/src/test/{java,resources} and in frontend.
Such a commit message must carry a line `Acceptance-Test-Author: test-engineer` or `Acceptance-Ruling: <ruling id>`.
Merge commits are skipped. Usage: acceptance_guard.py <base-ref> [head-ref]  |  acceptance_guard.py --self-test"""
import os
import re
import subprocess
import sys
import tempfile

ACCEPTANCE = re.compile(r"^(backend/src/test/(java|resources)|frontend)/(?!.*node_modules/)(.+/)?acceptance/.+")
TRAILER = re.compile(r"^(Acceptance-Test-Author: test-engineer|Acceptance-Ruling: \S+)\s*$", re.M)


def git(*args, cwd=None):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, check=True).stdout


def violations(base, head="HEAD", cwd=None):
    found = []
    for sha in git("rev-list", "--no-merges", "--reverse", f"{base}..{head}", cwd=cwd).split():
        files = [f for f in git("diff-tree", "--no-commit-id", "--name-only", "-r", "--root", sha, cwd=cwd).splitlines() if ACCEPTANCE.match(f)]
        if files and not TRAILER.search(git("log", "-1", "--format=%B", sha, cwd=cwd)):
            found.append((sha, git("log", "-1", "--format=%s", sha, cwd=cwd).strip(), files))
    return found


def commit(repo, files, message):
    for path, text in files.items():
        full = os.path.join(repo, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w") as f:
            f.write(text)
    git("add", "-A", cwd=repo)
    git("commit", "-q", "-m", message, cwd=repo)


def self_test():
    backend = "backend/src/test/java/com/forcitis/one/platform_numbering/acceptance/NumberingAcceptanceTest.java"
    frontend = "frontend/apps/h5/src/acceptance/login.spec.ts"
    with tempfile.TemporaryDirectory() as repo:
        git("init", "-q", "-b", "main", cwd=repo)
        git("config", "user.email", "ci@example.invalid", cwd=repo)
        git("config", "user.name", "ci", cwd=repo)
        commit(repo, {"README.md": "x\n"}, "base")
        git("checkout", "-q", "-b", "feature", cwd=repo)

        def found():
            return [(message, files) for _, message, files in violations("main", "HEAD", repo)]

        commit(repo, {"backend/src/main/java/A.java": "a\n", "backend/src/test/java/com/forcitis/one/UnitTest.java": "a\n"}, "implementation only")
        commit(repo, {"frontend/node_modules/x/acceptance/y.js": "a\n", "backend/src/test/java/com/forcitis/one/acceptance_notes.txt": "a\n"}, "lookalikes")
        assert found() == [], "files that only look like acceptance tests must pass"
        commit(repo, {backend: "a\n"}, "backend acceptance without trailer")
        assert found() == [("backend acceptance without trailer", [backend])]
        commit(repo, {frontend: "a\n"}, "frontend acceptance, empty ruling\n\nAcceptance-Ruling: ")
        assert [m for m, _ in found()] == ["backend acceptance without trailer", "frontend acceptance, empty ruling"]
        git("reset", "-q", "--hard", "HEAD~2", cwd=repo)
        commit(repo, {backend: "b\n"}, "test engineer\n\nAcceptance-Test-Author: test-engineer")
        commit(repo, {frontend: "b\n"}, "ruling\n\nAcceptance-Ruling: RULING-12")
        git("rm", "-q", backend, cwd=repo)
        git("commit", "-q", "-m", "deleting an acceptance test counts\n\nAcceptance-Test-Author: someone-else", cwd=repo)
        assert [m for m, _ in found()] == ["deleting an acceptance test counts"]
        git("reset", "-q", "--hard", "HEAD~1", cwd=repo)
        assert found() == []
        git("checkout", "-q", "main", cwd=repo)
        commit(repo, {"docs.md": "x\n"}, "main moves on")
        git("merge", "-q", "--no-ff", "--no-commit", "feature", cwd=repo)
        with open(os.path.join(repo, backend), "w") as f:
            f.write("merge resolution\n")
        git("add", "-A", cwd=repo)
        git("commit", "-q", "-m", "merge feature", cwd=repo)
        assert violations("main~1", "HEAD", repo) == [], "merge commits are skipped"
    print("acceptance_guard self-test: ok")
    return 0


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    if len(sys.argv) < 2:
        print("usage: acceptance_guard.py <base-ref> [head-ref]", file=sys.stderr)
        return 2
    found = violations(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "HEAD")
    for sha, subject, files in found:
        print(f"::error title=Acceptance tests::{sha[:10]} {subject} changes {', '.join(files)} without an Acceptance-Test-Author or Acceptance-Ruling line in the commit message")
    print(f"acceptance_guard: {len(found)} violating commit(s)")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
