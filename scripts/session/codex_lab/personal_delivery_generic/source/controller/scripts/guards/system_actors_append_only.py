#!/usr/bin/env python3
"""System actor catalogue guard (R-1b P2 review L-1). backend/architecture/system-actors.txt is append-only: an event already published names its
actor by the UUID of a line of this file, and the audit trail looks the name up from it, so the name and UUID of a line that exists on the base branch
must still be there unchanged. A rename is a new line next to the old one. The module and reason columns may be corrected. Comment and blank lines are free. The copy under backend/src/test/resources/architecture/
is a test fixture and is not checked.
Usage: system_actors_append_only.py <base-ref>  |  system_actors_append_only.py --self-test"""
import os
import sys
import tempfile

from ci_git import CiError, commit_of, comparison_point, git, git_ok, require_full_history, toplevel

CATALOGUE = "backend/architecture/system-actors.txt"


def rows(text):
    found = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            found.append(" | ".join(field.strip() for field in stripped.split("|")[:2]))
    return found


def check(base, root):
    base_sha = commit_of(base, root)
    reference = comparison_point(base_sha, root)
    if not git_ok("cat-file", "-e", f"{reference}:{CATALOGUE}", cwd=root):
        return []
    before = rows(git("show", f"{reference}:{CATALOGUE}", cwd=root))
    path = os.path.join(root, CATALOGUE)
    if not os.path.exists(path):
        return [f"{CATALOGUE} is deleted; it is append-only"]
    with open(path, encoding="utf-8") as f:
        now = set(rows(f.read()))
    return [f"{CATALOGUE}: the name or UUID was removed or changed: {line}" for line in before if line not in now]


def run_check(base):
    root = toplevel()
    require_full_history(root)
    errors = check(base, root)
    for message in errors:
        print(f"::error title=System actor catalogue::{message}")
    print(f"system_actors_append_only: {len(errors)} violation(s)")
    return 1 if errors else 0


def new_repo(root):
    repo = tempfile.mkdtemp(dir=root)
    git("init", "-q", "-b", "main", cwd=repo)
    git("config", "user.email", "ci@example.invalid", cwd=repo)
    git("config", "user.name", "ci", cwd=repo)
    return repo


def write(repo, text):
    full = os.path.join(repo, CATALOGUE)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        f.write(text)


def expect(repo, errors, text=None, label=""):
    git("add", "-A", cwd=repo)
    found = check("main", repo)
    assert len(found) == errors, f"{label}: expected {errors} violation(s), got {found}"
    if text:
        assert any(text in message for message in found), f"{label}: no message contains {text!r}: {found}"
    git("reset", "-q", "--hard", "main", cwd=repo)


BASE = "# header\na.one | 1 | a | first\nb.two | 2 | b | second\n"


def self_test():
    with tempfile.TemporaryDirectory() as root:
        repo = new_repo(root)
        write(repo, BASE)
        git("add", "-A", cwd=repo)
        git("commit", "-q", "-m", "base", cwd=repo)

        write(repo, BASE)
        expect(repo, 0, label="unchanged")
        write(repo, BASE + "c.three | 3 | c | third\n")
        expect(repo, 0, label="a new line")
        write(repo, BASE.replace("a.one | 1 | a | first", "a.one | 1 | a | first\nrenamed.one | 1 | a | first"))
        expect(repo, 0, label="a rename is a new line and the old one stays")
        write(repo, BASE.replace("# header", "# a new header\n\n"))
        expect(repo, 0, label="comments and blank lines are free")
        write(repo, BASE.replace("a.one | 1 | a | first\n", ""))
        expect(repo, 1, text="a.one", label="a deleted line")
        write(repo, BASE.replace("a.one | 1 | a | first", "renamed.one | 1 | a | first"))
        expect(repo, 1, text="a.one", label="a renamed line without the old one")
        write(repo, BASE.replace("b | second", "b | changed reason"))
        expect(repo, 0, label="the module and reason columns may be corrected")
        write(repo, BASE.replace("b.two | 2 |", "b.two | 9 |"))
        expect(repo, 1, text="b.two", label="a changed UUID")
        git("rm", "-q", CATALOGUE, cwd=repo)
        expect(repo, 1, text="deleted", label="the file is deleted")
        try:
            check("no-such-ref", repo)
            raise AssertionError("an unknown base must fail")
        except CiError:
            pass
    print("system_actors_append_only self-test: ok")
    return 0


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    try:
        return run_check(sys.argv[1])
    except CiError as e:
        print(f"::error title=System actor catalogue::{e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
