#!/usr/bin/env python3
"""Agent-read rule files stay within the layer limits in policy.json `rules` (#810, #822; decision 2026-10-29-144628). 1 KB = 1024 UTF-8 bytes.

Layer A, read at every session start: root CLAUDE.md + every .claude/rules file without `paths:` + the start sheet at its cap
(`start.sheet_max_kb`; the sheet is cut to that, so the cap is its worst case). Limit `rules.layer_a_max_kb`.
Layer B, added when a session reads a file: the CLAUDE.md files in that file's folders below the root + every .claude/rules file
whose `paths:` matches it. Checked for every tracked file. Limit `rules.layer_b_max_kb`.
Layer C (docs read on demand, addressed by number) has no limit. MEMORY.md (outside the repository) is printed, never failed.

Each failure names the rule, what is over and the fix:
  RS-1 layer A over its limit
  RS-2 layer B over its limit for some files (one line per set of loaded files)
  RS-3 a .claude/rules file cannot be read (broken symlink) or its paths: front matter cannot be parsed

  rule_sizes.py              check the repository (run from its root)
  rule_sizes.py --self-test  a red case per rule
"""
import json
import os
import re
import subprocess
import sys
import tempfile

import yaml

KB = 1024
RULES_DIR = os.path.join(".claude", "rules")


def glob_regex(pattern):
    """A paths: glob as a regex over repository paths: ** spans folders, * and ? stay in one, {a,b} is either."""
    out, i = "", 0
    while i < len(pattern):
        c = pattern[i]
        if pattern.startswith("**/", i):
            out, i = out + "(?:.*/)?", i + 3
        elif pattern.startswith("**", i):
            out, i = out + ".*", i + 2
        elif c == "*":
            out, i = out + "[^/]*", i + 1
        elif c == "?":
            out, i = out + "[^/]", i + 1
        elif c == "{":
            end = pattern.find("}", i)
            if end < 0:
                raise ValueError(f"unclosed {{ in glob {pattern!r}")
            out, i = out + "(?:" + "|".join(re.escape(p) for p in pattern[i + 1:end].split(",")) + ")", end + 1
        else:
            out, i = out + re.escape(c), i + 1
    return re.compile(out + r"\Z")


def front_paths(text):
    """The paths: globs of a rules file, or None when it has none (it then loads at start)."""
    if not text.startswith("---\n"):
        return None
    end = text.find("\n---", 4)
    if end < 0:
        raise ValueError("front matter has no closing ---")
    meta = yaml.safe_load(text[4:end]) or {}
    paths = meta.get("paths") if isinstance(meta, dict) else None
    if paths is None:
        return None
    if isinstance(paths, str):
        paths = [p.strip() for p in re.split(r",(?![^{]*})", paths)]  # commas inside {a,b} do not split
    if not isinstance(paths, list) or not all(isinstance(p, str) and p for p in paths):
        raise ValueError("paths: is not a list of globs")
    return [glob_regex(p) for p in paths]


def rule_files(root, found):
    """(path, bytes, glob regexes or None) for each .claude/rules/**/*.md; unreadable ones go to found as RS-3."""
    out = []
    for folder, dirs, files in os.walk(os.path.join(root, RULES_DIR)):
        dirs.sort()
        for name in sorted(files):
            if not name.endswith(".md"):
                continue
            path = os.path.join(folder, name)
            rel = os.path.relpath(path, root)
            try:
                data = open(path, "rb").read()
                out.append((rel, len(data), front_paths(data.decode("utf-8"))))
            except (OSError, UnicodeDecodeError, ValueError, yaml.YAMLError) as e:
                found.append(f"RS-3 {rel}: cannot be read or parsed ({e}), so its size is not counted."
                             f" fix: repoint or remove the symlink, or write paths: as a YAML list of globs")
    return out


def kb_limit(policy, section, key):
    return int(policy[section][key] * KB)


def tracked(root):
    return subprocess.run(["git", "-C", root, "ls-files", "-z"], capture_output=True, check=True).stdout.decode().split("\0")[:-1]


def measure(root, files=None):
    """(problems, report lines) for the repository at root; files defaults to git ls-files."""
    policy = json.load(open(os.path.join(root, "policy.json"), encoding="utf-8"))
    a_max = kb_limit(policy, "rules", "layer_a_max_kb")
    b_max = kb_limit(policy, "rules", "layer_b_max_kb")
    sheet = kb_limit(policy, "start", "sheet_max_kb")
    found, report = [], []
    rules = rule_files(root, found)

    root_md = os.path.join(root, "CLAUDE.md")
    parts = [("CLAUDE.md", os.path.getsize(root_md) if os.path.exists(root_md) else 0)]
    parts += [(rel, size) for rel, size, globs in rules if globs is None]
    parts.append(("start sheet at start.sheet_max_kb", sheet))
    a = sum(size for _, size in parts)
    detail = " + ".join(f"{name} {size}" for name, size in parts)
    report.append(f"layer A {a} B / {a_max} B ({detail})")
    if a > a_max:
        found.append(f"RS-1 layer A is {a} B, over rules.layer_a_max_kb ({a_max} B): {detail}."
                     f" fix: shorten root CLAUDE.md or a start-loaded rule, or move text to a paths:-scoped rule or a numbered doc (layer C)")

    files = tracked(root) if files is None else files
    scoped = [(rel, size, regexes) for rel, size, regexes in rules if regexes is not None]
    nested = {os.path.dirname(f) for f in files
              if os.path.basename(f) == "CLAUDE.md" and os.path.dirname(f)}
    sets = {}  # loaded files -> (bytes, an example path)
    for f in files:
        folders = f.split("/")[:-1]
        loaded = tuple(d + "/CLAUDE.md" for d in ("/".join(folders[:n]) for n in range(1, len(folders) + 1)) if d in nested)
        loaded += tuple(rel for rel, _, regexes in scoped if any(r.match(f) for r in regexes))
        if loaded and loaded not in sets:
            size = sum(os.path.getsize(os.path.join(root, p)) for p in loaded)
            sets[loaded] = (size, f)
    for loaded, (size, example) in sorted(sets.items(), key=lambda kv: -kv[1][0]):
        detail = " + ".join(f"{p} {os.path.getsize(os.path.join(root, p))}" for p in loaded)
        report.append(f"layer B {size} B / {b_max} B (e.g. {example}: {detail})")
        if size > b_max:
            found.append(f"RS-2 layer B is {size} B for files such as {example}, over rules.layer_b_max_kb ({b_max} B): {detail}."
                         f" fix: shorten those files, narrow a rule's paths:, or move text to a numbered doc (layer C)")
    return found, report


def memory_note(root):
    """MEMORY.md of the main checkout's Claude project, printed for information only."""
    try:
        common = subprocess.run(["git", "-C", root, "rev-parse", "--path-format=absolute", "--git-common-dir"],
                                capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None
    slug = re.sub(r"[^A-Za-z0-9]", "-", os.path.dirname(common))
    path = os.path.expanduser(os.path.join("~", ".claude", "projects", slug, "memory", "MEMORY.md"))
    return f"MEMORY.md {os.path.getsize(path)} B (information only, not checked)" if os.path.exists(path) else None


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        def write(rel, text):
            path = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "w", encoding="utf-8").write(text)

        def rules_hit(files, a=6, b=7, sheet=4):
            json.dump({"rules": {"layer_a_max_kb": a, "layer_b_max_kb": b}, "start": {"sheet_max_kb": sheet}},
                      open(os.path.join(tmp, "policy.json"), "w"))
            return sorted({p.split()[0] for p in measure(tmp, files)[0]})

        scoped = '---\npaths:\n  - "backend/**"\n---\n'
        write("CLAUDE.md", "x" * 1000)
        write("backend/CLAUDE.md", "x" * 1000)
        write(".claude/rules/core.md", scoped + "x" * 5000)
        files = ["CLAUDE.md", "backend/CLAUDE.md", "backend/a/B.java", "docs/x.md"]
        assert rules_hit(files) == [], "1000 + 4096 sheet ≤ 6 KB; 1000 + 5000 + header ≤ 7 KB"
        assert rules_hit(files, sheet=6) == ["RS-1"], "the sheet cap counts toward layer A"
        write(".claude/rules/start.md", "x" * 2000)
        assert rules_hit(files) == ["RS-1"], "a rule without paths: loads at start"
        os.remove(os.path.join(tmp, ".claude/rules/start.md"))
        assert rules_hit(files, b=5) == ["RS-2"], "directory CLAUDE.md + matching rule add up"
        assert rules_hit(["CLAUDE.md", "docs/x.md"], b=1) == [], "files no rule matches load nothing extra"
        assert rules_hit(["backend/CLAUDE.md"], b=5) == ["RS-2"], "a folder's own CLAUDE.md counts when reading it"
        write("backend/a/CLAUDE.md", "x" * 1200)
        assert rules_hit(files + ["backend/a/CLAUDE.md"]) == ["RS-2"], "nested CLAUDE.md files stack along the path"
        os.remove(os.path.join(tmp, "backend/a/CLAUDE.md"))
        write(".claude/rules/core.md", '---\npaths: "frontend/**, docs/{a,b}/*.md"\n---\n' + "x" * 7200)
        assert rules_hit(files) == [], "a comma string of globs; backend no longer matches"
        assert rules_hit(files + ["docs/a/y.md"]) == ["RS-2"], "brace globs match"
        assert rules_hit(files + ["docs/a/z/y.md"]) == [], "* stays in one folder"
        os.remove(os.path.join(tmp, ".claude/rules/core.md"))
        os.symlink("../../gone.md", os.path.join(tmp, ".claude/rules/core.md"))
        assert rules_hit(files) == ["RS-3"], "a broken symlink is not silently 0 B"
        os.remove(os.path.join(tmp, ".claude/rules/core.md"))
        write(".claude/rules/core.md", "---\npaths: [\n---\n")
        assert rules_hit(files) == ["RS-3"], "unparsable front matter"
        write(".claude/rules/core.md", '---\npaths: ["backend/{a"]\n---\n')
        assert rules_hit(files) == ["RS-3"], "an unclosed brace"
        write(".claude/rules/core.md", scoped + "x" * 7000)
        assert "fix: shorten those files" in measure(tmp, files)[0][0], "a failure says how to fix it"
        assert glob_regex("**/*.java").match("A.java") and glob_regex("**/*.java").match("a/b/A.java")
        assert not glob_regex("backend/**").match("backend-x/a")
    print("rule_sizes self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    found, report = measure(os.getcwd())
    for line in report[:6]:
        print(line)
    note = memory_note(os.getcwd())
    if note:
        print(note)
    for p in found:
        print(p)
    print(f"rule_sizes: {len(found)} problems")
    sys.exit(1 if found else 0)
