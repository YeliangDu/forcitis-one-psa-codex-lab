#!/usr/bin/env python3
"""Flyway guard (SESSION_PLAYBOOK §12.2). Fails when
  - a migration that already exists on the base branch was changed, renamed or deleted, unless the base branch carries
    the baseline-open marker (then the baseline may be edited directly);
  - the marker is added although the base branch has never had one (it may be added once, never reopened);
  - a migration file name is not V{yyyyMMddHHmmss}__{description}.sql, or two migrations share a version;
  - an added migration's version is not greater than the highest one on the base branch (warning only while the
    baseline is open: merging the baseline produces low versions).
Usage: migrations_immutable.py [base-ref]  |  migrations_immutable.py --self-test"""
import os
import re
import subprocess
import sys
import tempfile

MIGRATIONS = "backend/src/main/resources/db/migration/"
MARKER = "backend/architecture/migration-baseline-open"
FILE_NAME = re.compile(r"V(\d{14})__[A-Za-z0-9_]+\.sql")


def git(*args, cwd=None):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, check=True).stdout


def git_ok(*args, cwd=None):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True).returncode == 0


def baseline_open(base, cwd=None):
    return git_ok("cat-file", "-e", f"{base}:{MARKER}", cwd=cwd)


def marker_reopened(base, cwd=None):
    if baseline_open(base, cwd) or not os.path.exists(os.path.join(cwd or ".", MARKER)):
        return False
    return bool(git("log", "--full-history", "--format=%H", base, "--", MARKER, cwd=cwd).strip())


def changed_migrations(base, cwd=None):
    out = git("diff", "--name-status", "--no-renames", base, "--", MIGRATIONS, cwd=cwd)
    return [tuple(line.split("\t", 1)) for line in out.splitlines()]


def migration_files(cwd=None, base=None):
    if base:
        out = git("ls-tree", "-r", "--name-only", base, "--", MIGRATIONS, cwd=cwd)
    else:
        out = git("ls-files", "--cached", "--others", "--exclude-standard", "--", MIGRATIONS, cwd=cwd)
        out = "\n".join(p for p in out.splitlines() if os.path.exists(os.path.join(cwd or ".", p)))
    return sorted(set(out.splitlines()))


def version_of(path):
    match = FILE_NAME.fullmatch(os.path.basename(path))
    return match.group(1) if match else None


def check(base, cwd=None):
    errors, warnings = [], []
    is_open = baseline_open(base, cwd)
    if marker_reopened(base, cwd):
        errors.append(f"{MARKER} is added but {base} has had a baseline-open marker before; the baseline cannot be reopened")
    if not is_open:
        errors += [f"{status} {path}: changes a migration that is already on {base}; add a new migration instead"
                   for status, path in changed_migrations(base, cwd) if status != "A"]

    current = migration_files(cwd)
    seen = {}
    for path in current:
        version = version_of(path)
        if version is None:
            errors.append(f"{path}: file name must be V{{yyyyMMddHHmmss}}__{{description}}.sql")
        elif version in seen:
            errors.append(f"{path}: version {version} is also used by {seen[version]}")
        else:
            seen[version] = path

    base_versions = [v for v in map(version_of, migration_files(cwd, base)) if v]
    highest = max(base_versions, default=None)
    if highest:
        added = [path for status, path in changed_migrations(base, cwd) if status == "A"]
        for path in added:
            version = version_of(path)
            if version and version <= highest:
                (warnings if is_open else errors).append(f"{path}: version {version} is not greater than {highest}, the highest on {base}")
    return errors, warnings


def run(base):
    errors, warnings = check(base)
    for message in warnings:
        print(f"::warning title=Flyway version order (baseline open)::{message}")
    for message in errors:
        print(f"::error title=Flyway migrations::{message}")
    print(f"migrations_immutable: baseline {'open' if baseline_open(base) else 'closed'}, {len(errors)} violation(s), {len(warnings)} warning(s)")
    return 1 if errors else 0


def write(repo, path, text="SELECT 1;\n"):
    full = os.path.join(repo, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        f.write(text)


def commit(repo, message):
    git("add", "-A", cwd=repo)
    git("commit", "-q", "-m", message, cwd=repo)


def new_repo(root):
    repo = tempfile.mkdtemp(dir=root)
    git("init", "-q", "-b", "main", cwd=repo)
    git("config", "user.email", "ci@example.invalid", cwd=repo)
    git("config", "user.name", "ci", cwd=repo)
    return repo


def reset(repo):
    git("reset", "-q", "--hard", "main", cwd=repo)
    git("clean", "-qfd", cwd=repo)


def expect(repo, errors=0, warnings=0, text=None, label=""):
    git("add", "-A", cwd=repo)
    found, warned = check("main", repo)
    assert (len(found), len(warned)) == (errors, warnings), f"{label}: expected {errors} error(s) / {warnings} warning(s), got {found} / {warned}"
    if text:
        assert any(text in message for message in found + warned), f"{label}: no message contains {text!r}: {found + warned}"
    reset(repo)


def self_test():
    with tempfile.TemporaryDirectory() as root:
        run_cases(root)
    print("migrations_immutable self-test: ok")
    return 0


def run_cases(root):
    first = f"{MIGRATIONS}m/V20260101000000__m_create.sql"
    second = f"{MIGRATIONS}m/V20260102000000__m_add.sql"
    older = f"{MIGRATIONS}m/V20251231000000__m_older.sql"

    closed = new_repo(root)
    write(closed, first)
    commit(closed, "base")
    write(closed, second)
    expect(closed, label="append in closed baseline")
    write(closed, first, "-- edited\n")
    expect(closed, errors=1, text="M ", label="edit in closed baseline")
    os.remove(os.path.join(closed, first))
    expect(closed, errors=1, text="D ", label="delete in closed baseline")
    write(closed, older)
    expect(closed, errors=1, text="not greater", label="lower version in closed baseline")
    write(closed, f"{MIGRATIONS}m/V20260101000000__m_same_version.sql")
    expect(closed, errors=2, text="also used", label="duplicate version (also lower or equal)")
    write(closed, f"{MIGRATIONS}m/V2026__bad.sql")
    expect(closed, errors=1, text="file name must be", label="malformed name")

    opened = new_repo(root)
    write(opened, first)
    write(opened, MARKER, "open\n")
    commit(opened, "base with marker")
    write(opened, first, "-- edited\n")
    expect(opened, label="edit while baseline open")
    os.remove(os.path.join(opened, first))
    write(opened, f"{MIGRATIONS}m/V20250101000000__m_baseline.sql")
    expect(opened, warnings=1, text="not greater", label="baseline merge while open warns only")
    write(opened, f"{MIGRATIONS}m/V20260101000000__m_dup.sql")
    expect(opened, errors=1, warnings=1, text="also used", label="duplicate version fails even while open")
    os.remove(os.path.join(opened, MARKER))
    expect(opened, label="marker removal is always allowed")
    os.remove(os.path.join(opened, MARKER))
    write(opened, first, "-- edited\n")
    expect(opened, label="marker removed in the same change as a baseline edit follows the base state")

    git("rm", "-q", MARKER, cwd=opened)
    commit(opened, "remove marker")
    write(opened, MARKER, "open\n")
    expect(opened, errors=1, text="cannot be reopened", label="marker cannot be re-added after removal")
    write(opened, first, "-- edited\n")
    expect(opened, errors=1, text="M ", label="edit after marker removal")

    fresh = new_repo(root)
    write(fresh, first)
    commit(fresh, "base without marker ever")
    write(fresh, MARKER, "open\n")
    expect(fresh, label="first addition of the marker is allowed")


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    return run(sys.argv[1] if len(sys.argv) > 1 else "origin/main")


if __name__ == "__main__":
    sys.exit(main())
