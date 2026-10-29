#!/usr/bin/env python3
"""File and directory names across the repository (lead decision, #742 PR 3; whole tree once #755 moved docs).

Rules, each failure names the rule and the corrected name:
  N1 scripts/, tools/: a script (.py .sh .mjs .js .ts) is snake_case
  N2 scripts/, tools/: a self-test is <name>_selftest.<ext> (not test_<name> or <name>_test)
  N3 docs/: every file is lower-kebab-case whatever its format; a .md has no dotted variant (.notes.md, .keep.md, .template.md);
     no dotfiles (.gitkeep: docs/ holds files, not empty folders). Dotfiles elsewhere are not named by these rules
  N4 docs/: a directory is lower-kebab-case and carries no stage or session name (phase1a, r0, u8c, one-ops ...)
  N5 a top-level directory under docs/, scripts/ or tools/ is not on the allowlist: ask lead
Exceptions: README.md and CLAUDE.md; ADR-NNNN*.md; fragments under docs/decisions/, docs/glossary/issues/ and docs/ops/legacy-read/; frontend/ keeps
its package conventions. Every tracked file and every untracked one git does not ignore is checked, with all its
directories (#755 renamed the rest, PR B moved the last files out of docs/modernization/).

  check_names.py              exit 1 on a broken rule, 2 when git cannot list the files (fails closed)
  check_names.py --self-test
"""
import contextlib
import json
import os
import re
import subprocess
import sys
import tempfile

SCRIPT_SUFFIXES = (".py", ".sh", ".mjs", ".js", ".ts")
SNAKE = re.compile(r"[a-z0-9]+(_[a-z0-9]+)*")
KEBAB = re.compile(r"[a-z0-9]+(-[a-z0-9]+)*")
TEST_WORD = re.compile(r"^test_|_test$")
STAGE = re.compile(r"(phase\d+[a-z]?|r\d+[a-z]?|u8c)")
EXEMPT_NAMES = {"README.md", "CLAUDE.md"}
ADR = re.compile(r"ADR-\d{4}(-.*)?\.md")
FRAGMENT_DIRS = ("docs/decisions/", "docs/glossary/issues/", "docs/ops/legacy-read/")  # dated fragments keep their generated names
ALLOWED = {  # top-level directories; a new one needs lead's yes
    "docs": {"adr", "architecture", "business", "data", "decisions", "glossary", "legacy", "ops", "rules"},
    "scripts": {"ci", "codegen", "gates", "guards", "hooks", "merge", "release", "review", "session"},
    "tools": {"docs", "evidence", "ops"},
}


def snake(stem):
    return re.sub(r"_+", "_", re.sub(r"[^a-z0-9]+", "_", re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", stem).lower())).strip("_")


def kebab(stem):
    return snake(stem).replace("_", "-")


def selftest_name(stem):
    return re.sub(r"^test_|_test$", "", snake(stem)) + "_selftest"


def session_names(policy):
    sessions = policy.get("sessions", {})
    return set(sessions.get("config", {}))


def file_problems(path, sessions, dirs):
    """Rule breaks of one path; dirs are the directories whose own names are checked (all of them, in check())."""
    parts, found = path.split("/"), []
    name = parts[-1]
    stem, ext = os.path.splitext(name)
    top = parts[0]
    if top == "frontend":
        return found
    for depth in range(1, len(parts)):
        parent, child = "/".join(parts[:depth]), parts[depth]
        directory = parent + "/" + child
        if depth == len(parts) - 1 or directory not in dirs:
            continue
        if parent in ALLOWED and child not in ALLOWED[parent]:
            found.append(f"N5 {directory}/: top-level directory under {parent}/ is not on the allowlist in scripts/guards/check_names.py; ask lead")
        if top == "docs":
            if not KEBAB.fullmatch(child):
                found.append(f"N4 {directory}/: directories under docs/ are lower-kebab-case; use {parent}/{kebab(child)}/")
            if any(STAGE.fullmatch(word) for word in child.split("-")) or any(re.search(rf"(^|-){re.escape(n)}(-|$)", child) for n in sessions):
                found.append(f"N4 {directory}/: no stage or session names in docs/ directories; name it after what it holds")
    if name in EXEMPT_NAMES or ADR.fullmatch(name) or path.startswith(FRAGMENT_DIRS):
        return found
    where = "/".join(parts[:-1])
    if name.startswith("."):  # dotfiles: refused under docs/ (sources of truth only), not named by these rules elsewhere
        if top == "docs":
            found.append(f"N3 {path}: docs/ holds files, not empty folders or tool settings: remove {name}"
                         + (" (empty directories have no place in docs/)" if name == ".gitkeep" else ""))
        return found
    if top in ("scripts", "tools") and ext in SCRIPT_SUFFIXES:
        if TEST_WORD.search(snake(stem)) and not stem.endswith("_selftest"):
            found.append(f"N2 {path}: a self-test is <name>_selftest{ext}; use {where}/{selftest_name(stem)}{ext}")
        elif not SNAKE.fullmatch(stem):
            found.append(f"N1 {path}: scripts are snake_case; use {where}/{snake(stem)}{ext}")
    if top == "docs":
        if ext == ".md" and "." in stem:
            found.append(f"N3 {path}: no dotted variants of a document; use {where}/{kebab(stem.split('.')[0])}.md, or a folder")
        elif not KEBAB.fullmatch(stem):
            found.append(f"N3 {path}: files under docs/ are lower-kebab-case, whatever the format; use {where}/{kebab(stem)}{ext}")
    return found


def clean_env(cwd=None):
    """The environment without GIT_* (a hook sets GIT_INDEX_FILE, GIT_DIR ...): git in another directory must not reach the outer
    repository, nor look above that directory for one. The real run (no cwd) keeps the caller's environment on purpose."""
    env = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
    if cwd:
        env["GIT_CEILING_DIRECTORIES"] = os.path.dirname(os.path.abspath(cwd))
    return env


def git(*args, cwd=None):
    return subprocess.run(["git", "-c", "core.quotepath=off", *args], capture_output=True, text=True, encoding="utf-8", cwd=cwd,
                          env=clean_env(cwd) if cwd else None)



def all_paths(cwd=None):
    """Tracked files and untracked ones git does not ignore; None when git cannot list them."""
    runs = [git("ls-files", cwd=cwd), git("ls-files", "--others", "--exclude-standard", cwd=cwd)]
    if any(r.returncode for r in runs):
        return None
    return sorted({p for r in runs for p in r.stdout.splitlines() if p})


def check(cwd=None):
    paths = all_paths(cwd)
    if paths is None:
        print("check_names: git cannot list the files here; failing closed", file=sys.stderr)
        return 2
    dirs = {"/".join(p.split("/")[:i]) for p in paths for i in range(1, p.count("/") + 1)}
    sessions = session_names(json.load(open(os.path.join(cwd or ".", "policy.json"), encoding="utf-8")))
    problems = [p for path in paths for p in file_problems(path, sessions, dirs)]
    for p in problems:
        print(p)
    print(f"check_names: {len(paths)} paths, {len(dirs)} directories, {len(problems)} problems")
    return 1 if problems else 0


def self_test():
    sessions = {"one-ops", "one-coordinator"}

    def rules(path, dirs=()):
        return [p.split()[0] for p in file_problems(path, sessions, set(dirs))]

    assert rules("scripts/codegen/generate-api.sh") == ["N1"] and "scripts/codegen/generate_api.sh" in file_problems("scripts/codegen/generate-api.sh", sessions, set())[0]
    assert rules("scripts/ci/BuildAll.py") == ["N1"] and "build_all.py" in file_problems("scripts/ci/BuildAll.py", sessions, set())[0]
    assert rules("scripts/release/ship_test.sh") == ["N2"] and "ship_selftest.sh" in file_problems("scripts/release/ship_test.sh", sessions, set())[0]
    assert rules("scripts/codegen/test_scan_aggregates.py") == ["N2"] and "scan_aggregates_selftest.py" in file_problems("scripts/codegen/test_scan_aggregates.py", sessions, set())[0]
    assert rules("tools/ops/runner_test.py") == ["N2"] and rules("scripts/codegen/scan_acceptance_tests.py") == [], "a scanner of tests is no self-test"
    assert rules("scripts/ci/light_selftest.sh") == [] and rules("scripts/ci/relevant_change.py") == [] and rules("scripts/release/allowed_signers") == []
    assert rules("docs/Some_Notes.md") == ["N3"] and "some-notes.md" in file_problems("docs/Some_Notes.md", sessions, set())[0]
    assert rules("docs/data/Foo_Bar.yaml") == ["N3"] and "foo-bar.yaml" in file_problems("docs/data/Foo_Bar.yaml", sessions, set())[0]
    assert rules("docs/data/c3-dev-db.sql") == [] and rules("docs/data/rows.csv") == []
    assert rules("docs/data/.gitkeep") == ["N3"] and "remove .gitkeep" in file_problems("docs/data/.gitkeep", sessions, set())[0]
    assert rules("docs/.prettierrc") == ["N3"] and rules("scripts/ci/.keep") == [] and rules("tools/ops/.gitignore") == []
    assert rules("docs/plan.notes.md") == ["N3"] and "no dotted variants" in file_problems("docs/brief.template.md", sessions, set())[0]
    assert rules("docs/merge-queue.md") == [] and rules("docs/x/README.md") == []
    assert rules("docs/adr/ADR-0080.md") == [] and rules("docs/adr/ADR-0081-naming.md") == []
    assert rules("docs/decisions/2026-10-29-120000-one-ops-中文.md") == [] and rules("docs/ops/legacy-read/2026-10-29-1-one-ops-DAT.md") == []
    assert rules("docs/glossary/issues/2026-10-21-191312-one-architecture-CMD-命令.resolution.md") == [] and rules("docs/data/issues/X_Y.md") == ["N3"], "only the listed fragment folders are exempt"
    assert rules("docs/decisions/Phase1a/a.md", ["docs/decisions/Phase1a"]) == ["N4"] and rules("docs/ops/legacy-read/Bad_Dir/a.md", ["docs/ops/legacy-read/Bad_Dir"]) == ["N4"], "fragment folders exempt file names, not new directories"
    assert rules("docs/data/Ops_Notes/a.md", ["docs/data/Ops_Notes"]) == ["N4"]
    assert rules("docs/data/phase2/a.md", ["docs/data/phase2"]) == ["N4"]
    assert rules("docs/data/one-ops/a.md", ["docs/data/one-ops"]) == ["N4"]
    assert rules("docs/data/phase2/a.md") == [], "a directory that already exists at the base is legacy: phase 1 leaves it"
    for compound in ("phase1a-infra", "notes-phase2", "u8c-map", "r0-review", "one-ops-notes", "x-one-coordinator"):
        assert rules(f"docs/data/{compound}/a.md", [f"docs/data/{compound}"]) == ["N4"], compound
    assert rules("docs/data/release-notes/a.md", ["docs/data/release-notes"]) == [], "words that only contain a stage-like or session-like part are fine"
    assert rules("docs/stuff/a.md", ["docs/stuff"]) == ["N5"] and rules("docs/ops/a.md", ["docs/ops"]) == [], "the target layout (#755) is on the allowlist"
    assert rules("scripts/misc/a.py", ["scripts/misc"]) == ["N5"] and "ask lead" in file_problems("scripts/misc/a.py", sessions, {"scripts/misc"})[0]
    assert rules("docs/other/a.md", ["docs/other"]) == ["N5"] and rules("tools/misc/a.py", ["tools/misc"]) == ["N5"]
    assert rules("frontend/scripts/check-copy-glossary.mjs") == [] and rules("frontend/apps/web/src/App.tsx") == []
    with tempfile.TemporaryDirectory() as tmp, open(os.devnull, "w") as quiet, contextlib.redirect_stdout(quiet), contextlib.redirect_stderr(quiet):
        assert check(tmp) == 2, "no repository: fails closed"
        subprocess.run(["git", "init", "-q", tmp], check=True, env=clean_env())

        def touch(path):
            os.makedirs(os.path.dirname(os.path.join(tmp, path)), exist_ok=True)
            with open(os.path.join(tmp, path), "w"):
                pass

        with open(os.path.join(tmp, "policy.json"), "w") as f:
            json.dump({"sessions": {"config": {"one-ops": {}}}}, f)
        for path in ("docs/ops/runbooks/release.md", "scripts/ci/light.sh"):
            touch(path)
        assert check(tmp) == 0, "good names pass"
        for path in ("docs/ops/BadEvent.yaml", "docs/ops/New_Stuff/a.md", "docs/modernization/a.md"):
            touch(path)
            assert check(tmp) == 1, f"a bad name, a bad directory or a top-level directory off the allowlist is found: {path}"
            os.remove(os.path.join(tmp, path))
        subprocess.run(["git", "-C", tmp, "add", "-A"], check=True, env=clean_env())
        touch("docs/ops/Old_Runbooks/release.md")
        assert check(tmp) == 1, "an untracked file and its directory are checked like tracked ones"
        os.remove(os.path.join(tmp, "docs/ops/Old_Runbooks/release.md"))
        subprocess.run(["git", "-C", tmp, "-c", "user.name=t", "-c", "user.email=t@example.invalid", "-c", "core.hooksPath=", "-c", "commit.gpgsign=false", "commit", "-qm", "x"], check=True, env=clean_env())
        assert check(tmp) == 0, "committed and clean"
        subprocess.run(["git", "-C", tmp, "mv", "docs/ops/runbooks/release.md", "docs/ops/runbooks/RELEASE.md"], check=True, env=clean_env())
        subprocess.run(["git", "-C", tmp, "-c", "user.name=t", "-c", "user.email=t@example.invalid", "-c", "core.hooksPath=", "-c", "commit.gpgsign=false", "commit", "-qm", "y"], check=True, env=clean_env())
        assert check(tmp) == 1, "a file already in the repository is checked, not only added ones"
    assert rules("docs/data/r2dbc/a.md", ["docs/data/r2dbc"]) == [] and rules("scripts/ci/intent/BadName.py") == ["N1"]
    print("check_names self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif len(sys.argv) == 1:
        sys.exit(check())
    else:
        sys.exit(__doc__)
