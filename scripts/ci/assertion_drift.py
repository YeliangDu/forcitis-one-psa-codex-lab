#!/usr/bin/env python3
"""Warn (never fail) when a change removes or weakens test assertions; ask for the PR template field 「改动的断言」."""
import json
import os
import re
import subprocess
import sys

TEST_FILE = re.compile(r"(src/test/.*\.java|\.test\.tsx?|\.spec\.tsx?)$")
ASSERTION = re.compile(r"\b(assertThat|assertEquals|assertTrue|assertFalse|assertThrows|assertThatThrownBy|verify|expect)\s*\(")
EXACT = re.compile(r"\.(isEqualTo|containsExactly|containsExactlyInAnyOrder|hasSize|isZero|isEmpty|toBe|toEqual|toHaveLength)\(")
RANGE = re.compile(r"\.(isGreaterThan|isGreaterThanOrEqualTo|isLessThan|isLessThanOrEqualTo|isPositive|isNotEmpty|contains|atLeast|atMost|toBeGreaterThan|toBeGreaterThanOrEqual|toContain)\(")
FIELD = "改动的断言 / 测试及理由"


def diff(base, head="HEAD"):
    out = subprocess.run(["git", "diff", "--unified=0", f"{base}...{head}"], capture_output=True, text=True, check=True).stdout
    files, current = {}, None
    for line in out.splitlines():
        if line.startswith("+++ b/"):
            current = line[6:] if TEST_FILE.search(line[6:]) else None
            if current:
                files.setdefault(current, {"added": [], "removed": []})
        elif current and line.startswith("+") and not line.startswith("+++"):
            files[current]["added"].append(line[1:])
        elif current and line.startswith("-") and not line.startswith("---"):
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


def main():
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
