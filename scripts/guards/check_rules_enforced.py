#!/usr/bin/env python3
"""Every rule in docs/rules/rules.md and docs/rules/roles/*.md names an enforcer that exists (#856): a rule whose checker is gone only
looks enforced.

An "Enforced by:" clause runs to the end of its sentence; its parts are separated by ";". Each part is either
`manual: <role> / <cadence>` or a comma list of names, and each name must be one of:
  - a script under scripts/ or tools/ (its file name without the suffix, e.g. post_review);
  - a job of a .github/workflows file (e.g. backend, gitleaks);
  - a tool-library program in TOOL_LIBRARY (outside the repository); where the library exists (local runs) its file must exist.

Each failure names the rule, the place and the fix:
  RE-1 a name is no script, workflow job or tool-library program
  RE-2 a manual part is not `manual: <role> / <cadence>`
  RE-3 a TOOL_LIBRARY entry's file is missing (local runs only)

  check_rules_enforced.py              check the repository (run from its root)
  check_rules_enforced.py --self-test  a red case per rule
"""
import glob
import os
import re
import sys
import tempfile

SOURCES = ("docs/rules/rules.md", "docs/rules/roles/*.md")
LIBRARY = os.path.expanduser("~/.modernize/psa/tools")
TOOL_LIBRARY = {  # name -> file in the tool library
    "handoff": "one-session/handoff/handoff.py",
    "start_sheet": "one-session/start_sheet/start_sheet.py",
}
CLAUSE = re.compile(r"Enforced by: (.*?)(?:\.(?:\s|$)|$)", re.M)
MANUAL = re.compile(r"manual: [^/;]+ / [^/;]+")
NAME = re.compile(r"[a-z][a-z0-9_-]*")


def scripts(root):
    names = set()
    for top in ("scripts", "tools"):
        for folder, dirs, files in os.walk(os.path.join(root, top)):
            dirs[:] = [d for d in dirs if d not in ("node_modules", "__pycache__")]
            names.update(os.path.splitext(f)[0] for f in files)
    return names


def jobs(root):
    found = set()
    for path in glob.glob(os.path.join(root, ".github", "workflows", "*.y*ml")):
        text = open(path, encoding="utf-8").read()
        block = re.search(r"^jobs:\n(.*)", text, re.M | re.S)
        if block:
            found.update(re.findall(r"^  ([A-Za-z0-9_-]+):\s*$", block.group(1), re.M))
    return found


def clauses(root):
    for pattern in SOURCES:
        for path in sorted(glob.glob(os.path.join(root, pattern))):
            rel = os.path.relpath(path, root)
            text = open(path, encoding="utf-8").read()
            for m in CLAUSE.finditer(text):
                yield rel, text.count("\n", 0, m.start()) + 1, m.group(1)


def problems(root, library=LIBRARY, tool_library=TOOL_LIBRARY):
    known = scripts(root) | jobs(root) | set(tool_library)
    found = []
    for rel, line, clause in clauses(root):
        for part in (p.strip() for p in clause.split(";")):
            if part.startswith("manual"):
                if not MANUAL.fullmatch(part):
                    found.append(f"RE-2 {rel}:{line}: `{part}` is not `manual: <role> / <cadence>`. fix: name who checks it and how often")
                continue
            for name in (n.strip() for n in part.split(",")):
                if not NAME.fullmatch(name) or name not in known:
                    found.append(f"RE-1 {rel}:{line}: enforcer `{name}` is no script under scripts/ or tools/, no workflow job and no tool-library"
                                 f" program, so the rule only looks enforced. fix: name the check that enforces it, or `manual: <role> / <cadence>`")
    if os.path.isdir(library):
        for name, path in tool_library.items():
            if not os.path.exists(os.path.join(library, path)):
                found.append(f"RE-3 TOOL_LIBRARY {name}: {path} is missing from {library}. fix: point the entry at the program, or drop it")
    return found


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        def write(rel, text):
            path = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "w", encoding="utf-8").write(text)

        def hit(rules, library=None, tool_library=None):
            write("docs/rules/rules.md", rules)
            return sorted({p.split()[0] for p in problems(tmp, library or os.path.join(tmp, "nolib"), tool_library or {})})

        write("scripts/guards/post_review.py", "")
        write("tools/x/mq.sh", "")
        write(".github/workflows/ci.yml", "on: push\njobs:\n  backend:\n    runs-on: x\n  gitleaks:\n    runs-on: x\n")
        write("docs/rules/roles/dev.md", "1. A rule. Enforced by: backend, post_review.\n")
        assert hit("## a\nEnforced by: mq, gitleaks; manual: lead / every PR\n") == [], "scripts, jobs and a manual part"
        assert hit("Enforced by: build_all, gitleaks\n") == ["RE-1"], "a script that is gone"
        assert hit("Enforced by: manual: lead\n") == ["RE-2"], "a manual part without a cadence"
        assert hit("1. Rule. Enforced by: backend. Next sentence names nothing.\n") == [], "the clause ends with its sentence"
        assert hit("Enforced by: handoff\n", tool_library={"handoff": "h/handoff.py"}) == [], "a tool-library program"
        os.makedirs(os.path.join(tmp, "lib"))
        assert hit("Enforced by: handoff\n", os.path.join(tmp, "lib"), {"handoff": "h/handoff.py"}) == ["RE-3"], "its file is missing"
        write("docs/rules/roles/dev.md", "1. A rule. Enforced by: frontend.\n")
        assert hit("Enforced by: mq\n") == ["RE-1"], "role files are read too"
        assert "fix: name the check" in problems(tmp, os.path.join(tmp, "nolib"), {})[0], "the fix is named"
    print("check_rules_enforced self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    found = problems(os.getcwd())
    for p in found:
        print(p)
    print(f"check_rules_enforced: {len(found)} problems")
    sys.exit(1 if found else 0)
