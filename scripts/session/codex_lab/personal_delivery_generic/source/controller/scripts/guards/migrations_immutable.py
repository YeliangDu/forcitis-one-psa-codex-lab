#!/usr/bin/env python3
"""Flyway guard (SESSION_PLAYBOOK 附 M3「迁移基线」). Fails when
  - a migration that already exists on the base branch was changed, renamed or deleted, unless the base branch carries
    the baseline-open marker (then the baseline may be edited directly);
  - the marker is added although the base branch has had one before (it may be added once, never reopened), or a file
    differs from the marker name only in case;
  - the marker is removed together with any other migration change (removing the marker must be its own pull request);
  - a migration file name is not V{yyyyMMddHHmmss}__{description}.sql with a real timestamp, or two migrations
    (db/migration and the test data folder) share a version;
  - an added migration's version is not greater than the highest one on the base branch (warning only while the
    baseline is open: merging the baseline produces low versions);
  - the migration folder is empty, spring.flyway.locations is not exactly classpath:db/migration, or a Java migration exists
    (the checks above only see SQL files in that folder).
Usage: migrations_immutable.py <base-ref>  |  migrations_immutable.py --self-test"""
import os
import re
import sys
import tempfile
from datetime import datetime

from ci_git import CiError, commit_of, comparison_point, git, git_ok, require_full_history, run, toplevel

MIGRATIONS = "backend/src/main/resources/db/migration/"
TESTDATA = "backend/src/test/resources/db/testdata/"
RESOURCES = "backend/src/main/resources/"
SOURCES = "backend/src/"
MARKER = "backend/architecture/migration-baseline-open"
MARKER_DIR = "backend/architecture/"
LOCATIONS = "classpath:db/migration"
FILE_NAME = re.compile(r"V(\d{14})__[A-Za-z0-9_]+\.sql")
PROPERTIES = re.compile(r"application.*\.(properties|ya?ml)")


def tree_files(root, prefix, ref=None):
    if ref:
        names = git("ls-tree", "-r", "--name-only", ref, "--", prefix, cwd=root).splitlines()
    else:
        listed = git("ls-files", "--cached", "--others", "--exclude-standard", "--", prefix, cwd=root).splitlines()
        names = [name for name in listed if os.path.lexists(os.path.join(root, name))]
    return sorted(set(names))


def baseline_open(base_sha, root):
    return git_ok("cat-file", "-e", f"{base_sha}:{MARKER}", cwd=root)


def marker_in_tree(root):
    return MARKER in tree_files(root, MARKER_DIR)


def marker_case_variants(root):
    return [name for name in tree_files(root, MARKER_DIR) if name != MARKER and name.lower() == MARKER.lower()]


def marker_reopened(base_sha, root):
    if baseline_open(base_sha, root) or not marker_in_tree(root):
        return False
    return bool(git("log", "--full-history", "--format=%H", base_sha, "--", MARKER, cwd=root).strip())


def changed_migrations(reference, root):
    out = git("diff", "--name-status", "--no-renames", reference, "--", MIGRATIONS, cwd=root)
    return [tuple(line.split("\t", 1)) for line in out.splitlines()]


def version_of(path):
    match = FILE_NAME.fullmatch(os.path.basename(path))
    if not match:
        return None
    try:
        datetime.strptime(match.group(1), "%Y%m%d%H%M%S")
    except ValueError:
        return None
    return match.group(1)


def read(root, path):
    with open(os.path.join(root, path), encoding="utf-8", errors="replace") as f:
        return f.read()


def flyway_configuration_problems(root):
    problems = []
    if not tree_files(root, MIGRATIONS):
        problems.append(f"no migration file under {MIGRATIONS}; the folder must not be empty")
    for path in tree_files(root, RESOURCES):
        name = os.path.basename(path)
        if not PROPERTIES.fullmatch(name):
            continue
        text = read(root, path)
        if name.endswith(".properties"):
            values = [m.group(1).strip() for m in re.finditer(r"^\s*spring\.flyway\.locations\s*[=:]\s*(.*)$", text, re.M)]
            problems += [f"{path}: spring.flyway.locations is {v!r}, must be exactly {LOCATIONS}" for v in values if v != LOCATIONS]
        elif "flyway" in text.lower():
            problems.append(f"{path}: Flyway settings in YAML are not checked; use application*.properties")
    java = run("grep", "--untracked", "-l", "-E", "JavaMigration", "--", SOURCES, cwd=root)
    problems += [f"{path}: Java migrations are not allowed; write SQL migrations" for path in java.stdout.splitlines()]
    return problems


def check(base, root):
    errors, warnings = [], []
    base_sha = commit_of(base, root)
    reference = comparison_point(base_sha, root)
    is_open = baseline_open(base_sha, root)
    changes = changed_migrations(reference, root)

    if marker_reopened(base_sha, root):
        errors.append(f"{MARKER} is added but {base} has had a baseline-open marker before; the baseline cannot be reopened")
    errors += [f"{name} differs from {MARKER} only in case; the marker is matched exactly" for name in marker_case_variants(root)]
    if is_open and not marker_in_tree(root) and changes:
        errors.append(f"{MARKER} is removed together with {len(changes)} migration change(s); removing the baseline-open marker must be its own pull request")
    if not is_open:
        errors += [f"{status} {path}: changes a migration that is already on {base}; add a new migration instead"
                   for status, path in changes if status != "A"]

    seen = {}
    for path in tree_files(root, MIGRATIONS) + tree_files(root, TESTDATA):
        version = version_of(path)
        if version is None:
            errors.append(f"{path}: file name must be V{{yyyyMMddHHmmss}}__{{description}}.sql with a valid timestamp")
        elif version in seen:
            errors.append(f"{path}: version {version} is also used by {seen[version]}")
        else:
            seen[version] = path

    highest = max((v for v in map(version_of, tree_files(root, MIGRATIONS, base_sha)) if v), default=None)
    if highest:
        for status, path in changes:
            version = version_of(path)
            if status == "A" and version and version <= highest:
                (warnings if is_open else errors).append(f"{path}: version {version} is not greater than {highest}, the highest on {base}")
    errors += flyway_configuration_problems(root)
    return errors, warnings


def run_check(base):
    root = toplevel()
    require_full_history(root)
    errors, warnings = check(base, root)
    for message in warnings:
        print(f"::warning title=Flyway version order (baseline open)::{message}")
    for message in errors:
        print(f"::error title=Flyway migrations::{message}")
    state = "open" if baseline_open(commit_of(base, root), root) else "closed"
    print(f"migrations_immutable: baseline {state}, {len(errors)} violation(s), {len(warnings)} warning(s)")
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
    git("config", "core.ignorecase", "false", cwd=repo)
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
    renamed = f"{MIGRATIONS}m/V20260103000000__m_create_renamed.sql"
    properties = f"{RESOURCES}application.properties"

    closed = new_repo(root)
    write(closed, first)
    write(closed, second)
    write(closed, properties, f"spring.flyway.locations={LOCATIONS}\n")
    commit(closed, "base")
    write(closed, f"{MIGRATIONS}m/V20260104000000__m_appended.sql")
    expect(closed, label="append in closed baseline")
    write(closed, first, "-- edited\n")
    expect(closed, errors=1, text="M ", label="edit in closed baseline")
    os.remove(os.path.join(closed, first))
    expect(closed, errors=1, text="D ", label="delete in closed baseline")
    os.remove(os.path.join(closed, first))
    write(closed, renamed)
    expect(closed, errors=1, text="D ", label="rename in closed baseline is a delete of the old file")
    write(closed, older)
    expect(closed, errors=1, text="not greater", label="lower version in closed baseline")
    write(closed, f"{MIGRATIONS}m/V20260101000000__m_same_version.sql")
    expect(closed, errors=2, text="also used", label="duplicate version in closed baseline (also not greater)")
    write(closed, f"{TESTDATA}V20260101000000__testdata_same_version.sql")
    expect(closed, errors=1, text="also used", label="test data folder shares the version space")
    write(closed, f"{MIGRATIONS}m/V2026__bad.sql")
    expect(closed, errors=1, text="file name must be", label="malformed name")
    write(closed, f"{MIGRATIONS}m/V20261340000000__m_bad_date.sql")
    expect(closed, errors=1, text="valid timestamp", label="impossible timestamp")
    write(closed, properties, "spring.flyway.locations=classpath:db/migration,classpath:db/testdata\n")
    expect(closed, errors=1, text="spring.flyway.locations", label="extra Flyway location")
    write(closed, "backend/src/main/java/x/Fix.java", "class Fix extends BaseJavaMigration {}\n")
    expect(closed, errors=1, text="Java migrations", label="Java migration")
    try:
        check("no-such-ref", closed)
        raise AssertionError("an unknown base must fail")
    except CiError:
        pass
    git("clone", "-q", "--depth", "1", f"file://{closed}", os.path.join(root, "shallow"), cwd=root)
    try:
        require_full_history(os.path.join(root, "shallow"))
        raise AssertionError("a shallow checkout must fail")
    except CiError:
        pass

    ahead = new_repo(root)
    write(ahead, first)
    write(ahead, properties, f"spring.flyway.locations={LOCATIONS}\n")
    commit(ahead, "base")
    git("checkout", "-q", "-b", "feature", cwd=ahead)
    git("checkout", "-q", "main", cwd=ahead)
    write(ahead, second)
    commit(ahead, "main moves on")
    git("checkout", "-q", "feature", cwd=ahead)
    write(ahead, f"{MIGRATIONS}m/V20260103000000__m_third.sql")
    git("add", "-A", cwd=ahead)
    found, warned = check("main", ahead)
    assert (found, warned) == ([], []), f"a branch behind main must not see main's new migrations as deleted: {found} {warned}"
    write(ahead, f"{MIGRATIONS}m/V20260101120000__m_between.sql")
    git("add", "-A", cwd=ahead)
    found, _ = check("main", ahead)
    assert len(found) == 1 and "not greater" in found[0], found

    bare = new_repo(root)
    write(bare, "README.md", "x\n")
    commit(bare, "base")
    expect(bare, errors=1, text="no migration file", label="empty migration folder")

    opened = new_repo(root)
    write(opened, first)
    write(opened, MARKER, "open\n")
    commit(opened, "base with marker")
    write(opened, first, "-- edited\n")
    expect(opened, label="edit while baseline open")
    os.remove(os.path.join(opened, first))
    write(opened, f"{MIGRATIONS}m/V20250101000000__m_baseline.sql")
    expect(opened, warnings=1, text="not greater", label="baseline merge while open warns only")
    os.remove(os.path.join(opened, first))
    write(opened, renamed.replace("20260103", "20250103"))
    expect(opened, warnings=1, text="not greater", label="rename (delete and add) while open")
    write(opened, f"{MIGRATIONS}m/V20260101000000__m_dup.sql")
    expect(opened, errors=1, warnings=1, text="also used", label="duplicate version fails even while open")
    os.remove(os.path.join(opened, MARKER))
    expect(opened, label="marker removal on its own is allowed")
    os.remove(os.path.join(opened, MARKER))
    write(opened, first, "-- edited\n")
    expect(opened, errors=1, text="its own pull request", label="marker removal together with a baseline edit fails")
    os.remove(os.path.join(opened, MARKER))
    write(opened, f"{MIGRATIONS}m/V20270101000000__m_new.sql")
    expect(opened, errors=1, text="its own pull request", label="marker removal together with a new migration fails")
    git("rm", "-q", MARKER, cwd=opened)
    write(opened, MARKER_DIR + "Migration-Baseline-Open", "open\n")
    expect(opened, errors=1, text="only in case", label="marker renamed by case")
    git("rm", "-q", MARKER, cwd=opened)
    write(opened, MARKER_DIR + "Migration-Baseline-Open", "open\n")
    write(opened, first, "-- edited\n")
    expect(opened, errors=2, text="only in case", label="marker renamed by case together with a baseline edit")

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
    write(fresh, MARKER, "open\n")
    write(fresh, first, "-- edited\n")
    expect(fresh, errors=1, text="M ", label="adding the marker does not open the baseline for the same change")


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    try:
        return run_check(sys.argv[1])
    except CiError as e:
        print(f"::error title=Flyway migrations::{e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
