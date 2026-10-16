#!/usr/bin/env python3
"""Warn (never fail) when a change removes or weakens test assertions; ask for the PR template field 「改动的断言」."""
import json
import os
import re
import sys
import tempfile
from pathlib import Path

from ci_git import git, toplevel

TEST_FILE = re.compile(r"(src/test/.*\.java|\.test\.tsx?|\.spec\.tsx?)$")
ASSERTION = re.compile(r"\b(assertThat|assertEquals|assertTrue|assertFalse|assertThrows|assertThatThrownBy|verify|expect)\s*\(")
EXACT = re.compile(r"\.(isEqualTo|containsExactly|containsExactlyInAnyOrder|hasSize|isZero|isEmpty|toBe|toEqual|toHaveLength)\(")
RANGE = re.compile(r"\.(isGreaterThan|isGreaterThanOrEqualTo|isLessThan|isLessThanOrEqualTo|isPositive|isNotEmpty|contains|atLeast|atMost|toBeGreaterThan|toBeGreaterThanOrEqual|toContain)\(")
FIELD = "改动的断言 / 测试及理由"


def unquote(path):
    """git still quotes a path holding a quote, backslash or control character even with core.quotePath=false."""
    if not (path.startswith('"') and path.endswith('"')):
        return path
    return re.sub(r"\\([\\\"tn])", lambda m: {"t": "\t", "n": "\n"}.get(m.group(1), m.group(1)), path[1:-1])


def diff(base, head="HEAD", cwd="."):
    out = git("diff", "--unified=0", f"{base}...{head}", cwd=toplevel(cwd))
    files, old, current, header = {}, None, None, False
    for line in out.splitlines():
        if line.startswith("diff --git "):
            header, old, current = True, None, None
        elif line.startswith("@@"):
            header = False
        elif header and line.startswith("--- "):
            old = unquote(line[4:].rstrip("\t"))
            old = old[2:] if old.startswith("a/") else None
        elif header and line.startswith("+++ "):
            path = unquote(line[4:].rstrip("\t"))
            path = path[2:] if path.startswith("b/") else old
            current = path if path and TEST_FILE.search(path) else None
            if current:
                files.setdefault(current, {"added": [], "removed": []})
        elif current and not header and line.startswith("+"):
            files[current]["added"].append(line[1:])
        elif current and not header and line.startswith("-"):
            files[current]["removed"].append(line[1:])
    return files


def findings(files):
    notes = []
    for path, lines in files.items():
        removed = sum(bool(ASSERTION.search(l)) for l in lines["removed"])
        added = sum(bool(ASSERTION.search(l)) for l in lines["added"])
        if removed > added:
            notes.append(f"{path}: assertions {removed} removed, {added} added")
        exact_removed = sum(bool(EXACT.search(l)) for l in lines["removed"])
        range_added = sum(bool(RANGE.search(l)) for l in lines["added"])
        exact_added = sum(bool(EXACT.search(l)) for l in lines["added"])
        if exact_removed > exact_added and range_added:
            notes.append(f"{path}: exact assertions replaced by range assertions ({exact_removed} exact removed, {range_added} range added)")
    return notes


def pr_field_filled():
    event = os.environ.get("GITHUB_EVENT_PATH")
    if not event or not os.path.exists(event):
        return None
    try:
        with open(event, encoding="utf-8") as f:
            body = (json.load(f).get("pull_request") or {}).get("body") or ""
    except (OSError, ValueError):
        return None
    m = re.search(rf"##\s*{re.escape(FIELD)}\s*\n(.*?)(\n##\s|\Z)", body, re.S)
    if not m:
        return False
    text = re.sub(r"<!--.*?-->", "", m.group(1), flags=re.S).strip()
    return bool(text) and text not in ("无", "-", "N/A")


def self_test():
    chinese = "backend/src/test/java/验收/金额Test.java"
    gone = "frontend/src/a.test.ts"
    dashes = "backend/src/test/java/DashesTest.java"
    kept = "backend/src/test/java/KeptTest.java"
    quoted = 'backend/src/test/java/Quote"dTest.java'
    before = {
        chinese: "assertThat(a).isEqualTo(1);\nassertThat(b).isEqualTo(2);\n",
        gone: "expect(x).toBe(1);\n",
        dashes: "-- assertThat(c).isTrue();\n-- assertThat(d).isTrue();\n",
        kept: "assertThat(e).isTrue();\n",
        quoted: "assertThat(f).isTrue();\n",
    }
    after = {chinese: "assertThat(a).isEqualTo(1);\n", gone: None, dashes: "++ x\n", kept: "assertThat(e).isTrue();\nassertThat(g).isTrue();\n", quoted: "x\n"}
    with tempfile.TemporaryDirectory() as repo:
        git("init", "-q", "-b", "main", cwd=repo)
        git("config", "user.email", "ci@example.invalid", cwd=repo)
        git("config", "user.name", "ci", cwd=repo)
        git("config", "commit.gpgsign", "false", cwd=repo)
        for path, text in before.items():
            Path(repo, path).parent.mkdir(parents=True, exist_ok=True)
            Path(repo, path).write_text(text, encoding="utf-8")
        git("add", "-A", cwd=repo)
        git("commit", "-q", "-m", "base", cwd=repo)
        git("checkout", "-q", "-b", "change", cwd=repo)
        for path, text in after.items():
            if text is None:
                Path(repo, path).unlink()
            else:
                Path(repo, path).write_text(text, encoding="utf-8")
        git("commit", "-q", "-am", "change", cwd=repo)
        changed = diff("main", "HEAD", cwd=repo)
        notes = findings(changed)
    assert f"{chinese}: assertions 1 removed, 0 added" in notes, f"a test under a non-ASCII path must be read: {notes}"
    assert f"{gone}: assertions 1 removed, 0 added" in notes, f"a deleted test file must be counted: {notes}"
    assert f"{dashes}: assertions 2 removed, 0 added" in notes, f"content lines starting with -- or ++ are not file headers: {notes}"
    assert f"{quoted}: assertions 1 removed, 0 added" in notes, f"a path git still quotes must be unquoted, not skipped: {notes}"
    assert kept in changed and not any(note.startswith(kept) for note in notes), f"a file that gains an assertion is read and not reported: {notes}"
    print("assertion_drift self-test: ok")
    return 0


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    base = sys.argv[1] if len(sys.argv) > 1 else "origin/main"
    head = sys.argv[2] if len(sys.argv) > 2 else "HEAD"
    notes = findings(diff(base, head))
    for note in notes:
        print(f"::warning title=Assertion drift::{note}")
    if notes and pr_field_filled() is False:
        print(f"::warning title=PR template::assertions were removed or weakened: fill in the 「{FIELD}」 section of the PR description")
    print(f"assertion drift: {len(notes)} finding(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
