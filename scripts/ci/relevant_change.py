#!/usr/bin/env python3
"""Decide whether a pull request touches anything the backend or frontend builds read. Writes relevant=true|false to
$GITHUB_OUTPUT. Everything is relevant except documentation: docs/ outside the paths the tests read, and root *.md.
Usage: relevant_change.py <base-ref>  |  relevant_change.py --self-test"""
import os
import subprocess
import sys

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


def self_test():
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
