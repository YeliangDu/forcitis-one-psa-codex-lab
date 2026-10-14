#!/usr/bin/env python3
"""Decide whether a pull request touches anything the backend or frontend builds read. Writes relevant=true|false to
$GITHUB_OUTPUT. Everything is relevant except documentation: docs/ outside the paths the tests read, and root *.md.
Usage: relevant_change.py <base-ref>  |  relevant_change.py --self-test"""
import os
import re
import subprocess
import sys
from pathlib import Path

READ_BY_BUILDS = (
    "docs/modernization/glossary/",
    "docs/modernization/events/",
    "docs/modernization/phase1a/SEEYON_VIEWS.sql",
)


def is_relevant(path):
    if path.startswith(READ_BY_BUILDS):
        return True
    if path.startswith("docs/"):
        return False
    return not ("/" not in path and path.endswith(".md"))


def changed_files(base):
    out = subprocess.run(["git", "diff", "--name-only", f"origin/{base}...HEAD"], capture_output=True, text=True, check=True)
    return out.stdout.splitlines()


SCANNED = ("backend/src", "frontend", "scripts")
# Release and deploy scripts read docs (deploy blockers, checklist, runbook files) but no ci job runs them; the self-test keeps it so
NOT_RUN_BY_CI = ("scripts/release/",)
SKIPPED_DIRS = {"node_modules", "dist", ".pnpm", "target"}
SOURCE_SUFFIXES = {".java", ".kt", ".ts", ".tsx", ".js", ".mjs", ".cjs", ".json", ".yaml", ".yml", ".py", ".sh", ".properties", ".xml", ".sql"}
LITERAL = re.compile(r"docs/modernization(?:/[\w.*{}-]+)*")
SEGMENTS = re.compile(r"""['"]docs['"]\s*,\s*['"]modernization['"]((?:\s*,\s*['"][\w.*-]+['"])*)""")


def is_covered(path):
    return path.startswith(READ_BY_BUILDS) or (path + "/").startswith(READ_BY_BUILDS)


def referenced_docs_paths(root):
    for scanned in SCANNED:
        for file in Path(root, scanned).rglob("*"):
            if not file.is_file() or file.suffix not in SOURCE_SUFFIXES or SKIPPED_DIRS & set(file.parts) or file.name == Path(__file__).name:
                continue
            if file.relative_to(root).as_posix().startswith(NOT_RUN_BY_CI):
                continue
            text = file.read_text(errors="ignore")
            for match in LITERAL.finditer(text):
                yield file, match.group(0)
            for match in SEGMENTS.finditer(text):
                tail = re.findall(r"['\"]([\w.*-]+)['\"]", match.group(1))
                yield file, "/".join(["docs/modernization", *tail])


def uncovered_reads(root):
    return sorted({f"{file.relative_to(root)}: {path}" for file, path in referenced_docs_paths(root) if not is_covered(path)})


def self_test():
    root = Path(__file__).resolve().parents[2]
    ci = (root / ".github/workflows/ci.yml").read_text()
    assert not any(p in ci for p in NOT_RUN_BY_CI), f"ci.yml runs {NOT_RUN_BY_CI}: their docs reads must be listed in READ_BY_BUILDS"
    missing = uncovered_reads(root)
    assert not missing, "builds read docs paths that READ_BY_BUILDS does not list (add them in scripts/ci/relevant_change.py):\n  " + "\n  ".join(missing)
    assert is_relevant("backend/pom.xml")
    assert is_relevant(".github/workflows/ci.yml")
    assert is_relevant("scripts/ci/relevant_change.py")
    assert is_relevant("docs/modernization/glossary/terms.yaml")
    assert is_relevant("docs/modernization/events/asyncapi.yaml")
    assert is_relevant("docs/modernization/phase1a/SEEYON_VIEWS.sql")
    assert not is_relevant("docs/modernization/adr/src/ADR-0001.md")
    assert not is_relevant("docs/modernization/phase1a/INTERFACES.md")
    assert not is_relevant("README.md")
    assert is_relevant("notes.txt")


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    base = sys.argv[1] if len(sys.argv) > 1 else ""
    if os.environ.get("GITHUB_EVENT_NAME") != "pull_request" or not base:
        relevant = True
    else:
        files = changed_files(base)
        relevant = any(is_relevant(f) for f in files)
        print(f"{len(files)} changed files, relevant={relevant}")
    line = f"relevant={'true' if relevant else 'false'}"
    print(line)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as out:
            out.write(line + "\n")


main()
