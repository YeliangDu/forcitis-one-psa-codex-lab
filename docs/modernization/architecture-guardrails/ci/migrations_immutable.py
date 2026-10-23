#!/usr/bin/env python3
"""Fail when a Flyway migration that already exists on the base branch was changed, renamed or deleted (append-only,
SESSION_PLAYBOOK 附 M3「迁移基线」). Usage: migrations_immutable.py [base-ref]  |  migrations_immutable.py --self-test"""
import os
import subprocess
import sys
import tempfile

MIGRATIONS = "backend/src/main/resources/db/migration/"


def git(*args, cwd=None):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, check=True).stdout


def violations(base, cwd=None):
    out = git("diff", "--name-status", "--no-renames", base, "--", MIGRATIONS, cwd=cwd)
    found = []
    for line in out.splitlines():
        status, path = line.split("\t", 1)
        if status != "A":
            found.append(f"{status} {path}")
    return found


def self_test():
    with tempfile.TemporaryDirectory() as repo:
        git("init", "-q", "-b", "main", cwd=repo)
        git("config", "user.email", "ci@example.invalid", cwd=repo)
        git("config", "user.name", "ci", cwd=repo)
        os.makedirs(os.path.join(repo, MIGRATIONS, "m"))
        first = os.path.join(repo, MIGRATIONS, "m", "V1__m_create.sql")
        open(first, "w").write("CREATE TABLE m.t (id uuid);\n")
        git("add", ".", cwd=repo)
        git("commit", "-q", "-m", "base", cwd=repo)
        open(os.path.join(repo, MIGRATIONS, "m", "V2__m_add.sql"), "w").write("ALTER TABLE m.t ADD COLUMN x int;\n")
        assert violations("main", repo) == [], "an appended migration must pass"
        open(first, "a").write("-- edited\n")
        assert violations("main", repo) == [f"M {MIGRATIONS}m/V1__m_create.sql"], "an edited migration must fail"
        git("checkout", "-q", "--", ".", cwd=repo)
        os.remove(first)
        assert violations("main", repo) == [f"D {MIGRATIONS}m/V1__m_create.sql"], "a deleted migration must fail"
    print("migrations_immutable self-test: ok")
    return 0


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    base = sys.argv[1] if len(sys.argv) > 1 else "origin/main"
    found = violations(base)
    for v in found:
        print(f"::error title=Flyway append-only::{v} changes a migration that is already on {base}; add a new migration instead")
    print(f"migrations_immutable: {len(found)} violation(s)")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
