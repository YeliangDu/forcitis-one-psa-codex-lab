#!/usr/bin/env python3
"""A path a document or script cites must exist on the tree (#1263 item 5): the old docs cited scripts, runbooks and files that had moved or never came.

What is read: backticked text in tracked .md/.yaml/.yml files, and whole string literals that are a file name with an extension in tracked .py/.sh files under scripts/ and tools/.
A cited path is any token in them of the form top/…/name whose first segment is a top-level entry of the repository (docs/, scripts/, backend/ …); a path that is not
rooted there (a link relative to the document, a URL, a remote or legacy-system path) is not read. Not read either: tokens with a glob, brace or placeholder next to them, a name cut off at - or _,
a path git ignores (build output), a line that says the file is gone (已删除, 已不在树内, git 历史, git show), the record folders EXEMPT, and scripts that carry their own
--self-test (their literals are fixtures).

Paths missing when this check arrived are listed in scripts/guards/cited_paths_known.txt (file, path, reason, tab separated); each may only go away.

Each failure names the rule, the document and the fix:
  CP-1 a cited path is missing and not in the known list
  CP-2 a known entry is stale: the file is gone, no longer cites the path, or the path exists now

  cited_paths.py              check the repository (run from its root)
  cited_paths.py --self-test  a red case per rule
"""
import os
import re
import subprocess
import sys
import tempfile

KNOWN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cited_paths_known.txt")
EXEMPT = ("docs/adr/", "docs/decisions/", "docs/legacy/", "docs/glossary/decision-sheets/", "docs/ops/legacy-read/")   # records: they cite what was true when they were written
TOKEN = re.compile(r"[A-Za-z0-9_.@-]+(?:/[A-Za-z0-9_.@-]+)+/?")
BACKTICK = re.compile(r"`([^`\n]+)`")
LITERAL = re.compile(r"""["']([^"'\n]+)["']""")
FILE_NAME = re.compile(r"[A-Za-z0-9_.@/-]+\.(md|yaml|yml|json|sh|py|sql|txt|toml|xml)")
GONE = re.compile(r"已删除|已不在树内|git 历史|git show")


def git(root, *args, stdin=None):
    return subprocess.run(["git", "-C", root, *args], capture_output=True, text=True, input=stdin, check=False).stdout


def load_known(path=KNOWN):
    known = {}
    if os.path.exists(path):
        for line in open(path, encoding="utf-8").read().splitlines():
            if line.strip() and not line.startswith("#"):
                doc, cited, reason = (line.split("\t") + ["", ""])[:3]
                known[(doc, cited)] = reason
    return known


def cited(root, files):
    """(file, line, path) for every path cited that is rooted in a top-level entry of the repository."""
    tops = {f.split("/")[0] for f in files if "/" in f}
    for rel in files:
        docs = rel.endswith((".md", ".yaml", ".yml")) and not rel.startswith(EXEMPT)
        scripts = rel.endswith((".py", ".sh")) and rel.startswith(("scripts/", "tools/")) and "selftest" not in rel
        if not (docs or scripts):
            continue
        try:
            text = open(os.path.join(root, rel), encoding="utf-8").read()
        except (OSError, UnicodeDecodeError):
            continue
        if scripts and "--self-test" in text:
            continue
        for number, line in enumerate(text.split("\n"), 1):
            if docs and GONE.search(line):
                continue
            for piece in (BACKTICK if docs else LITERAL).findall(line):
                if scripts and not FILE_NAME.fullmatch(piece):
                    continue
                for m in TOKEN.finditer(piece):
                    path = m.group(0).rstrip("/.")
                    before = piece[max(0, m.start() - 2):m.start()]
                    after = piece[m.end():m.end() + 1]
                    if path.split("/")[0] in tops and not before.endswith(("//", "..")) and not path.endswith(("-", "_")) and after not in ("{", "*", "<"):
                        yield rel, number, path


def problems(root, files=None, known=None):
    files = git(root, "ls-files").splitlines() if files is None else files
    known = load_known() if known is None else known
    present = set(files)
    for f in files:
        p = os.path.dirname(f)
        while p and p not in present:
            present.add(p)
            p = os.path.dirname(p)
    found, seen = [], set()
    candidates = sorted({(rel, path) for rel, _, path in cited(root, files) if path not in present})
    ignored = set(git(root, "check-ignore", "--stdin", stdin="\n".join(p for _, p in candidates)).splitlines()) if candidates else set()
    for rel, path in candidates:
        if path in ignored:
            continue
        seen.add((rel, path))
        if (rel, path) not in known:
            found.append(f"CP-1 {rel}: cites {path}, which is not on the tree. fix: cite the file that exists, or say it is gone (已删除 … git show <commit>^:<path>); "
                         f"a path outside this repository goes in scripts/guards/cited_paths_known.txt with the reason")
    live = {(rel, path) for rel, _, path in cited(root, files)}
    for rel, path in sorted(known):
        if rel not in present:
            found.append(f"CP-2 {rel}: listed in cited_paths_known.txt but the file is gone. fix: delete the entry for {path}")
        elif (rel, path) not in live:
            found.append(f"CP-2 {rel}: no longer cites {path}. fix: delete its entry in scripts/guards/cited_paths_known.txt")
        elif (rel, path) not in seen:
            found.append(f"CP-2 {rel}: {path} exists now. fix: delete its entry in scripts/guards/cited_paths_known.txt")
    return found


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        def write(rel, text):
            path = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "w", encoding="utf-8").write(text)

        def hit(known=None):
            subprocess.run(["git", "-C", tmp, "add", "-A"], check=True)
            return sorted({p.split()[0] for p in problems(tmp, known=known or {})})
        subprocess.run(["git", "init", "-q", tmp], check=True)
        write("docs/real.md", "x\n")
        write("docs/dir/x.md", "x\n")
        write("scripts/real.sh", "x\n")
        write(".gitignore", "build/\n")
        write("docs/a.md", "see `docs/real.md`, `scripts/real.sh --flag`, `docs/dir/`, `docs/real.md:12`.\n")
        assert hit() == [], "paths that exist are fine"
        write("docs/a.md", "see `docs/missing.md`.\n")
        assert hit() == ["CP-1"], "a missing path"
        assert "which is not on the tree" in problems(tmp, known={})[0] and "fix:" in problems(tmp, known={})[0], "a failure says how to fix it"
        write("docs/a.md", "see `docs/dir/gone/` and `docs/real.md`.\n")
        assert hit() == ["CP-1"], "a missing directory"
        for ok in ("`docs/x-*.md` `docs/real-` `docs/<id>.md`", "`../scripts/missing.sh` `https://example.com/docs/missing.md` `runbooks/missing.md` `other/missing.md`",
                   "`docs/missing.md` 已删除，见 git show abc^:docs/missing.md", "`build/out.txt`"):
            write("docs/a.md", ok + "\n")
            assert hit() == [], ok
        write("docs/adr/ADR-1.md", "`docs/missing.md`\n")
        assert hit() == [], "a record folder is not read"
        write("scripts/tool.py", "A = 'docs/missing.md'\nB = 'docs/real.md'\nC = 'docs/missing-prefix'\nD = 'docs/missing.md and more'\n")
        assert hit() == ["CP-1"], "a literal that is a file name is read"
        assert [p for p in problems(tmp, known={}) if "tool.py" in p][0].count("docs/missing.md") == 1 and len([p for p in problems(tmp, known={}) if "tool.py" in p]) == 1, \
            "a prefix, or a literal with more in it, is not a path"
        write("scripts/tool.py", "A = 'docs/missing.md'\nif '--self-test' in argv: pass\n")
        assert hit() == [], "a script with its own self-test holds fixtures"
        os.remove(os.path.join(tmp, "scripts/tool.py"))
        write("docs/a.md", "`docs/missing.md`\n")
        assert hit({("docs/a.md", "docs/missing.md"): "r"}) == [], "a known entry covers it"
        assert hit({("docs/a.md", "docs/missing.md"): "r", ("docs/b.md", "docs/x.md"): "r"}) == ["CP-2"], "an entry for a file that is gone"
        write("docs/a.md", "`docs/real.md`\n")
        assert hit({("docs/a.md", "docs/missing.md"): "r"}) == ["CP-2"], "an entry for a path the document no longer cites"
        write("docs/a.md", "`docs/real.md`\n")
        write("docs/real2.md", "x\n")
        write("docs/c.md", "`docs/real2.md`\n")
        assert hit({("docs/c.md", "docs/real2.md"): "r"}) == ["CP-2"], "an entry for a path that exists now"
        os.makedirs(os.path.join(tmp, "x"))
        write("x/known.txt", "# c\ndocs/a.md\tdocs/m.md\tlives elsewhere\n\n")
        assert load_known(os.path.join(tmp, "x/known.txt")) == {("docs/a.md", "docs/m.md"): "lives elsewhere"}, "the known file: comments and blank lines skipped"
    print("cited_paths self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    found = problems(os.getcwd())
    print("\n".join(found) if found else "cited_paths: 0 problems")
    sys.exit(1 if found else 0)
