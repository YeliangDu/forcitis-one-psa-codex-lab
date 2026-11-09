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

The rule tables of the coding standards are read too (#860): the Check column of every CS-x.y row of docs/rules/coding-standard.md
(and the `Check:` line of each prose CS block), and the "Enforced by" column of the implemented guard tables of
docs/rules/naming-standard.md §4. A cell is parts separated by ";", each `manual: review`, `planned: <ids>` or a comma list of
enforcers; text in (parentheses) is a note. An enforcer is a guard id or range (G1–G33, F1–F4) defined in an implemented table, or a
`backticked` name that resolves: `pnpm <script>`; `TestClass#member`; an ESLint rule named in an eslint config; a path; a file name;
a test class; a member of a backend test class; a workflow job. Classes resolve only under backend/src/test (production code is
no enforcer). Planned ids are those of coding-standard.md §8 and of the naming-standard.md planned table. docs/rules/coding-core.md
§A cites golden files as `Class#member` (main or test). A file, section or table that is gone or changed shape fails, so the check
cannot pass by reading nothing.
  RE-4 a Check cell is empty or has text that is no part of the grammar (e.g. "to be built": write `planned: <id>`), or a rule row
       or prose rule does not start with its bare id (`| CS-2.8 |`, `**CS-2.25 Title**`)
  RE-5 an enforcer does not resolve, or a guard id is not in an implemented table
  RE-6 a `planned:` id is in no planned table, or is also implemented (then name the guard)
  RE-7 a planned row says a `path` is "to be created" while the path exists (the plan is stale)
  RE-8 a golden file in coding-core.md §A is not `Class#member`, or the class or member does not exist
  RE-9 a file, section or table this check reads is missing or has no rows

  check_rules_enforced.py              check the repository (run from its root)
  check_rules_enforced.py --self-test  a red case per rule
"""
import glob
import json
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
    return found + table_problems(root)


CODING = "docs/rules/coding-standard.md"
NAMING = "docs/rules/naming-standard.md"
CORE = "docs/rules/coding-core.md"
SKIP_DIRS = {".git", "node_modules", "target", "build", "dist", "coverage", "__pycache__", ".claude"}
GUARD_ID = r"(?:G-[A-Z]+\d+|[GDF]\d+)"
ID_RANGE = re.compile(rf"({GUARD_ID})(?:\s*[–-]\s*({GUARD_ID}))?")
KEYWORDS = {"return", "new", "throw", "else", "case", "yield", "assert"}
FIX_CELL = "fix: write parts separated by \";\": `manual: review`, `planned: <ids>`, or enforcers (guard ids, `backticked` names); notes go in (parentheses)"


def expand(text):
    """The guard ids a cell item or row id stands for: G21–G24 is G21..G24 (same prefix); None when it is no id or range."""
    m = ID_RANGE.fullmatch(text.strip())
    if not m:
        return None
    if not m.group(2):
        return [m.group(1)]
    a, b = (re.fullmatch(r"(.*?)(\d+)", g).groups() for g in m.groups())
    if a[0] != b[0] or int(a[1]) > int(b[1]):
        return None
    return [f"{a[0]}{n}" for n in range(int(a[1]), int(b[1]) + 1)]


def rows(text, first=None):
    """(line, cells) of each table row, header and separator rows left out; with first, only rows whose first cell matches it."""
    lines = text.splitlines()
    for no, line in enumerate(lines, 1):
        if not line.startswith("|") or re.fullmatch(r"\|[\s|:-]*", line.strip()):
            continue
        if no < len(lines) and re.fullmatch(r"\|[\s|:-]*-[\s|:-]*", lines[no].strip()):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if first is None or re.fullmatch(first, cells[0]):
            yield no, cells


def section(text, start, end=r"^#{2,3} "):
    """The lines from the heading matching start to the next heading, with their line offset; None when the heading is gone."""
    m = re.search(start, text, re.M)
    if not m:
        return None, 0
    rest = text[m.end():]
    n = re.search(end, rest, re.M)
    return rest[:n.start()] if n else rest, text.count("\n", 0, m.end())


class Repo:
    """What enforcer names resolve against, read once."""

    def __init__(self, root):
        self.root, self.files, self.base = root, set(), {}
        for folder, dirs, files in os.walk(root):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
            for f in files:
                rel = os.path.relpath(os.path.join(folder, f), root).replace(os.sep, "/")
                self.files.add(rel)
                self.base.setdefault(f, []).append(rel)
        self.jobs = jobs(root)
        self.eslint = "".join(self.read(f) for f in sorted(self.files) if re.match(r"frontend/(.*/)?eslint\.config\.[cm]?js$", f))
        self.pnpm = set()
        for f in self.files:
            if re.fullmatch(r"frontend/(?:[^/]+/[^/]+/)?package\.json", f):
                self.pnpm.update(json.loads(self.read(f)).get("scripts", {}))
        self.tests = [f for f in sorted(self.files) if f.startswith("backend/src/test/") and f.endswith(".java")]

    def read(self, rel):
        return open(os.path.join(self.root, rel), encoding="utf-8").read()

    def declares(self, rel, member):
        for m in re.finditer(rf"([\w>\]]+)\s+{re.escape(member)}\s*[(=;]", self.read(rel)):
            if m.group(1) not in KEYWORDS:
                return True
        return False

    def classes(self, name, main=False):
        roots = ("backend/src/test/", "backend/src/main/") if main else ("backend/src/test/",)
        return [f for f in self.base.get(name + ".java", []) if f.startswith(roots)]

    def resolves(self, token, main=False):
        """Whether token names something that exists; classes only under backend/src/test unless main (golden files)."""
        if token.startswith("pnpm "):
            return token[5:] in self.pnpm
        if "#" in token:
            cls, _, member = token.partition("#")
            return any(self.declares(f, member) for f in self.classes(cls, main))
        if re.fullmatch(r"(@[\w-]+/)?[a-z][\w-]*(/[\w-]+)?", token) and re.search(rf"['\"]{re.escape(token)}['\"]", self.eslint):
            return True
        if "/" in token:
            return token.rstrip("/") in self.files or any(f.startswith(token.rstrip("/") + "/") for f in self.files)
        if re.fullmatch(r"[\w.-]+\.[a-z]{1,5}", token):
            return token in self.base
        if re.fullmatch(r"[A-Z]\w*", token):
            return bool(self.classes(token, main))
        if re.fullmatch(r"[a-z]\w*", token) and any(self.declares(f, token) for f in self.tests):
            return True
        return token in self.jobs


def cell_parts(cell):
    """(kind, items) per part of a Check cell; kind is manual, planned or guard; items are ids and `names`; None when a part does not parse."""
    names = []
    text = re.sub(r"`([^`]+)`", lambda m: names.append(m.group(1)) or f"\0{len(names) - 1}\0", cell)
    while True:
        stripped = re.sub(r"\s*\([^()]*\)", "", text)
        if stripped == text:
            break
        text = stripped
    out = []
    for part in (p.strip() for p in text.split(";")):
        if part == "manual: review":
            out.append(("manual", []))
            continue
        kind, body = ("planned", part[len("planned:"):]) if part.startswith("planned:") else ("guard", re.sub(r"^Guard:", "", part))
        items = []
        for item in (i.strip() for i in body.split(",")):
            tok = re.fullmatch(r"\0(\d+)\0", item)
            if tok and kind == "guard":
                items.append("`" + names[int(tok.group(1))])
            elif expand(item) is not None:
                items.extend(expand(item))
            else:
                return None
        out.append((kind, items))
    return out


GUARD_HEADINGS = (r"^### Backend, implemented.*$", r"^### Frontend, implemented.*$", r"^### Planned guards.*$")


def guard_tables(text, found):
    """(implemented ids -> (line, enforcer cell), planned ids) of naming-standard.md §4; shape problems go to found."""
    implemented, planned = {}, set()
    for heading in GUARD_HEADINGS:
        body, offset = section(text, heading, r"^## " if "Planned" in heading else r"^#{2,3} ")
        table = list(rows(body)) if body is not None else []
        if not table:
            found.append(f"RE-9 {NAMING}: no table rows under the heading /{heading[1:-2]}/. fix: restore the heading and its table, or update this check")
        for line, cells in table:
            ids = expand(cells[0])
            if not ids:
                found.append(f"RE-4 {NAMING}:{line + offset}: row `{cells[0]}` does not start with a bare guard id or range. fix: put the id alone in the first cell")
            elif "Planned" in heading:
                planned.update(ids)
            else:
                implemented.update((gid, (line + offset, cells[-1])) for gid in ids)
    return implemented, planned


def check_cells(repo, implemented, planned, cells):
    """Problems of each (place, cell) against the cell grammar and the repository."""
    found = []
    for place, cell in cells:
        parts = cell_parts(cell) if cell.strip() else None
        if parts is None:
            found.append(f"RE-4 {place}: " + (f"`{cell}` does not parse. " if cell.strip() else "names no check (a prose rule ends with a `Check: ` line). ") + FIX_CELL)
            continue
        for kind, items in parts:
            for item in items:
                if kind == "planned" and (item not in planned or item in implemented):
                    found.append(f"RE-6 {place}: planned `{item}` is {'implemented' if item in implemented else 'in no planned table'}."
                                 f" fix: name the guard that enforces it, or plan it in {CODING} §8 or {NAMING} §4")
                elif kind == "guard" and item.startswith("`") and not repo.resolves(item[1:]):
                    found.append(f"RE-5 {place}: enforcer `{item[1:]}` does not exist, so the rule only looks enforced."
                                 f" fix: name the real test, script, ESLint rule or job, or write `manual: review`")
                elif kind == "guard" and not item.startswith("`") and item not in implemented:
                    found.append(f"RE-5 {place}: guard {item} is in no implemented table of {NAMING} §4."
                                 f" fix: write `planned: {item}` if it is planned, or name the guard that enforces it")
    return found


def table_problems(root):
    missing = [f for f in (CODING, NAMING, CORE) if not os.path.exists(os.path.join(root, f))]
    if missing:
        return [f"RE-9 {f}: missing, so its rules are not checked. fix: restore it, or move this check to its new place" for f in missing]
    repo = Repo(root)
    coding, naming, core = (repo.read(f) for f in (CODING, NAMING, CORE))
    found = []
    implemented, planned = guard_tables(naming, found)
    planned |= {cells[0] for _, cells in rows(coding, r"G-CS\d+")}
    cells = []
    for line, c in rows(coding, r"\W*CS-.*"):
        if re.fullmatch(r"CS-\d+\.\d+", c[0]):
            cells.append((f"{CODING}:{line} {c[0]}", c[-1]))
        else:
            found.append(f"RE-4 {CODING}:{line}: rule row `{c[0]}` does not start with a bare CS-x.y id, so its Check cell would go unread."
                         f" fix: put the id alone in the first cell")
    lines = coding.splitlines()
    for no, line in enumerate(lines, 1):
        if not re.match(r"\W*CS-\d", line) or line.startswith("|"):
            continue
        block = re.match(r"\*\*(CS-\d+\.\d+) ", line)
        if not block:
            found.append(f"RE-4 {CODING}:{no}: prose rule `{line[:30]}` does not start `**CS-x.y Title`, so its Check line would go unread."
                         f" fix: start the line with ** and the bare id")
            continue
        check = next((i for i in range(no, len(lines)) if lines[i].startswith(("Check: ", "**", "#"))), None)
        if check is None or not lines[check].startswith("Check: "):
            cells.append((f"{CODING}:{no} {block.group(1)}", ""))
        else:
            cells.append((f"{CODING}:{check + 1} {block.group(1)}", lines[check][len("Check: "):]))
    if not cells:
        found.append(f"RE-9 {CODING}: no CS-x.y rule rows found. fix: restore the rule tables, or update this check")
    cells += [(f"{NAMING}:{line} {gid}", cell) for gid, (line, cell) in sorted(implemented.items())]
    found += check_cells(repo, implemented, planned, cells)
    body, offset = section(naming, GUARD_HEADINGS[2], r"^## ")
    for line, row in rows(body or ""):
        for path in re.findall(r"`([^`]+)` \(to be created", " ".join(row)):
            if repo.resolves(path, main=True):
                found.append(f"RE-7 {NAMING}:{line + offset} {row[0]}: `{path}` is \"to be created\" but exists."
                             f" fix: move the guard to an implemented table naming it, or correct the plan")
    body, offset = section(core, r"^## A\..*$", r"^## ")
    golden = list(rows(body)) if body is not None else []
    if not golden:
        found.append(f"RE-9 {CORE}: no golden-file table under `## A.`. fix: restore the section and its table, or update this check")
    for line, row in golden:
        for token in (re.findall(r"`([^`]+)`", row[1]) if len(row) > 1 else []) or [row[1] if len(row) > 1 else ""]:
            if not re.fullmatch(r"[A-Z]\w*#\w+", token) or not repo.resolves(token, main=True):
                found.append(f"RE-8 {CORE}:{line + offset} {row[0]}: golden file `{token}` is not an existing `Class#member`"
                             f" (line numbers drift). fix: cite the class and the method or field that shows the pattern")
    return found


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        def write(rel, text):
            path = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "w", encoding="utf-8").write(text)

        def hit(rules, library=None, tool_library=None):
            write("docs/rules/rules.md", rules)
            found = problems(tmp, library or os.path.join(tmp, "nolib"), tool_library or {})
            return sorted({p.split()[0] for p in found if p.split()[0] in ("RE-1", "RE-2", "RE-3")})  # the standards are absent here (RE-9)

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
    table_self_test()
    print("check_rules_enforced self-test: ok")


def table_self_test():
    with tempfile.TemporaryDirectory() as tmp:
        def write(rel, text):
            path = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "w", encoding="utf-8").write(text)

        write("backend/src/test/java/x/ArchTest.java", "class ArchTest {\n    static final ArchRule noCycles = rule();\n    @Test\n    void layers() { return check(); }\n}\n")
        write("backend/src/main/java/x/SubmitThing.java", "class SubmitThing {\n    public Result execute(Command c) {\n        return other.run(c);\n    }\n}\n")
        write("backend/src/test/java/x/CallTest.java", "class CallTest {\n    void a() {\n        return check(c);\n    }\n}\n")
        write("scripts/guards/file_lines.py", "")
        write("frontend/eslint.config.js", "rules: { 'no-restricted-syntax': ['error'] }\n")
        write("frontend/package.json", '{"scripts": {"typecheck": "tsc"}}')
        write(".github/workflows/ci.yml", "on: push\njobs:\n  gitleaks:\n    runs-on: x\n")
        naming = ("### Backend, implemented\n\n| # | Rule | Enforced by |\n|---|---|---|\n| G1 | r | `noCycles` |\n| G2 | r | `ArchTest#layers` (note) |\n\n"
                  "### Frontend, implemented\n\n| # | Rule | Enforced by |\n|---|---|---|\n| F1–F2 | r | `frontend/eslint.config.js` |\n\n"
                  "### Planned guards (not implemented)\n\n| # | Check | Source | Before | Owner |\n|---|---|---|---|---|\n| G3 | r | s | b | unscheduled |\n| G2 | r | s | b | unscheduled |\n{planned}\n## 5. Next\n")
        coding = ("| # | Rule | Source | Check |\n|---|---|---|---|\n| CS-1.1 | r | s | {cell} |\n\n**CS-2.20 Prose rule** (new)\n- text\n{prose}\n\n"
                  "| Id | Rule | Points |\n|---|---|---|\n| G-CS1 | CS-2.20 | p |\n")
        core = "## A. Golden files\n\n| Part | Class#member | Pattern |\n|---|---|---|\n| {part} | `{golden}` | p |\n\n## B. Rules\n"

        def hit(cell="Guard: G1, F1–F2, `ArchTest`, `file_lines.py`, `no-restricted-syntax`, `pnpm typecheck`, `gitleaks` (n, m); manual: review",
                prose="\nCheck: planned: G-CS1; Guard: `ArchTest#noCycles`", golden="SubmitThing#execute", planned="", part="Use case",
                coding_text=None, core_text=None):
            write(CODING, coding_text or coding.format(cell=cell, prose=prose))
            write(NAMING, naming.format(planned=planned))
            write(CORE, core_text or core.format(golden=golden, part=part))
            return sorted({p.split()[0] for p in table_problems(tmp)})

        assert hit() == [], "every enforcer kind resolves"
        assert hit(cell="#810 line-count check (to be built); Review") == ["RE-4"], "free text instead of an enforcer"
        assert hit(cell="") == ["RE-4"], "an empty Check cell"
        assert hit(prose="- no check line") == ["RE-4"], "a prose rule without a Check: line"
        assert hit(cell="Guard: `GoneTest`") == ["RE-5"], "a test class that does not exist"
        assert hit(cell="Guard: `ArchTest#gone`") == ["RE-5"], "a member that does not exist"
        assert hit(cell="Guard: `SubmitThing#run`") == ["RE-5"], "a call is no declaration"
        assert hit(cell="Guard: `pnpm lint`") == ["RE-5"], "a pnpm script that does not exist"
        assert hit(cell="Guard: `scripts/guards/gone.py`") == ["RE-5"], "a path that does not exist"
        assert hit(cell="Guard: G1–G3") == ["RE-5"], "a range that reaches a planned guard"
        assert hit(cell="planned: G9") == ["RE-6"], "a planned id in no planned table"
        assert hit(cell="planned: G2") == ["RE-6"], "a planned id that is also implemented (G2 is in both tables here)"
        assert hit(cell="Guard: `no-console`") == ["RE-5"], "an ESLint rule no eslint config names"
        assert hit(cell="Guard: `CallTest#check`") == ["RE-5"], "`return check(...)` is a call, not a declaration"
        assert hit(cell="Guard: G2–G1") == ["RE-4"], "a reversed range"
        assert hit(cell="Guard: `SubmitThing`") == ["RE-5"], "a production class is no enforcer"
        assert hit(cell="Guard: `SubmitThing#execute`") == ["RE-5"], "a production member is no enforcer"
        assert hit(coding_text=coding.format(cell="manual: review", prose="\nCheck: planned: G-CS1").replace("| CS-1.1 |", "| CS-1.1 (new) |")) == ["RE-4"], "a rule row whose id cell carries more than the id"
        for heading in ("**CS-2.20: ", "### CS-2.20 ", "- **CS-2.20 ", "CS-2.20 "):
            assert hit(coding_text=coding.format(cell="manual: review", prose="").replace("**CS-2.20 ", heading)) == ["RE-4"], f"a prose rule headed {heading!r}"
        assert hit(coding_text="no rules here\n") == ["RE-9"], "a standard with no rule rows"
        assert hit(part="Money (kernel)", golden="SubmitThing:2") == ["RE-8"], "every golden row is read, whatever its Part says"
        assert hit(core_text=core.format(golden="SubmitThing:2", part="Use case").replace("## A. Golden", "## A Golden")) == ["RE-9"], "a renamed §A heading"
        write(CORE, core.format(golden="SubmitThing#execute", part="Use case"))
        write(NAMING, naming.format(planned="").replace("### Frontend, implemented", "### Frontend guards"))
        assert sorted({p.split()[0] for p in table_problems(tmp)}) == ["RE-5", "RE-9"], "a renamed guard-table heading (its ids are then unknown too)"
        os.remove(os.path.join(tmp, CORE))
        assert sorted({p.split()[0] for p in table_problems(tmp)}) == ["RE-9"], "a missing coding-core.md"
        write(CORE, core.format(golden="SubmitThing#execute", part="Use case"))
        write(CODING, coding.format(cell="#810 line-count check (to be built)", prose="\nCheck: planned: G-CS1"))
        assert "RE-4" in {p.split()[0] for p in problems(tmp, os.path.join(tmp, "nolib"), {})}, "the check light.sh runs includes the tables"
        assert hit(planned="| G4 | `scripts/guards/file_lines.py` (to be created): count | s | b | unscheduled |") == ["RE-7"], "a stale plan"
        assert hit(golden="SubmitThing:2") == ["RE-8"], "a golden file by line number"
        assert hit(golden="SubmitThing#gone") == ["RE-8"], "a golden member that does not exist"
        assert hit(golden="SubmitThing") == ["RE-8"], "a golden class without its member"
        assert hit(planned="| G9 (old) | r | s | b | unscheduled |") == ["RE-4"], "a guard row whose id cell carries more than the id"


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    found = problems(os.getcwd())
    for p in found:
        print(p)
    print(f"check_rules_enforced: {len(found)} problems")
    sys.exit(1 if found else 0)
