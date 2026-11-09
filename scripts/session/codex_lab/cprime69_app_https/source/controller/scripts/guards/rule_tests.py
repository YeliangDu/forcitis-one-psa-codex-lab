#!/usr/bin/env python3
"""A business rule of a built bounded context has a test that cites it, or sits on a list that only shrinks (#1271).

A rule is DEFINED by a table row `| RULE-n | <domain> | ...` or a `##`/`###` heading `RULE-n` in docs/business/rules/*.md; text under a
"候选规则" (candidate) heading is not adopted and is not read. Its BC comes from the row's domain through DOMAIN_BC below (a vocabulary, not a
rule-to-test mapping); a BC is built when docs/architecture/ssot/domain/contexts.yaml says `status: built`. A rule is CITED when
scripts/codegen/scan_acceptance_tests.py finds its id in a test (the same collect() and cited_ids() that write gaps.md, no second parser).

  RT-1 a defined rule of a built BC is cited by no test and is not in rule_tests_known.txt
  RT-2 an id in rule_tests_known.txt is cited by a test now
  RT-3 an id in rule_tests_known.txt is not a defined rule of a built BC (gone, retired or its BC is not built)
  RT-4 an uncited rule has a domain or heading with no BC in DOMAIN_BC/RULE_BC, or either names a BC contexts.yaml does not have
  RT-5 rule_tests_known.txt has an id the base branch does not have (the list only shrinks; a new uncited rule needs its test)

  rule_tests.py <base-ref>   check the repository against that base (run from its root)
  rule_tests.py --self-test  a red case per rule
"""
import importlib.util
import os
import pathlib
import re
import subprocess
import sys
import tempfile

RULES = "docs/business/rules"
CONTEXTS = "docs/architecture/ssot/domain/contexts.yaml"
KNOWN = "scripts/guards/rule_tests_known.txt"
DOMAIN_BC = {  # the `domain` column of a rule row -> the BC that owns it (evidence: the row, contexts.yaml ids)
    "approval": "platform.approval",
    "identity": "platform.identity",
    "integration": "platform.integration",
    "numbering": "platform.numbering",
    "audit": "platform.audit",
    "attendance": "organization.attendance",
    "leave": "organization.leave",  # planned, not built: RT-1 asks for tests only once the BC is built
    "timesheet": "cost.timesheet",
    "org": "organization.workforce",
    "workforce": "organization.workforce",
    "opportunity": "presales.quoting",  # bid/no-bid decisions: presales, planned (lead ruling 2026-11-04); checked once presales is built
}
RULE_BC = {  # id-level override of the domain (lead ruling 2026-11-04)
    "RULE-416": "cost.costing",  # adjusts costs already posted in the open accounting period: the cost consumer, planned; its row says "organization"
}
ROW = re.compile(r"^\|\s*(RULE-\d+[a-z]?)\s*\|\s*([^|]*?)\s*\|")
HEADING = re.compile(r"^#{2,3}\s+(RULE-\d+[a-z]?)\b")
CANDIDATE = "候选规则"


def scanner(root):
    path = os.path.join(root, "scripts/codegen/scan_acceptance_tests.py")
    spec = importlib.util.spec_from_file_location("scan_acceptance_tests", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def definitions(root):
    """{rule id: domain or None} from the rule files; a heading-only rule has no domain."""
    found = {}
    directory = os.path.join(root, RULES)
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".md"):
            continue
        candidate = False
        with open(os.path.join(directory, name), encoding="utf-8") as f:
            for line in f:
                if line.startswith("#"):
                    candidate = CANDIDATE in line
                if candidate:
                    continue
                row, heading = ROW.match(line), HEADING.match(line)
                if row:
                    found[row[1]] = row[2] or None
                elif heading:
                    found.setdefault(heading[1], None)
    return found


def built_contexts(root):
    """(all context ids, built context ids), read from the top-level `- id:` / `  status:` lines."""
    ids, built, current = set(), set(), None
    with open(os.path.join(root, CONTEXTS), encoding="utf-8") as f:
        for line in f:
            if line.startswith("- id: "):
                current = line[6:].strip()
                ids.add(current)
            elif line.startswith("  status: ") and current and line[10:].strip() == "built":
                built.add(current)
    return ids, built


def known_ids(text):
    return [line.split("#")[0].strip() for line in text.splitlines() if line.split("#")[0].strip()]


def bc_of(rule, domain):
    return RULE_BC.get(rule) or DOMAIN_BC.get(domain)


def problems(defined, cited, known, contexts, base_known=None):
    ids, built = contexts
    found = [f"RT-4 domain {d!r} maps to {bc}, which {CONTEXTS} does not list. fix: correct DOMAIN_BC in scripts/guards/rule_tests.py"
             for d, bc in sorted(DOMAIN_BC.items()) if bc not in ids]
    found += [f"RT-4 {r} maps to {bc}, which {CONTEXTS} does not list. fix: correct RULE_BC in scripts/guards/rule_tests.py"
              for r, bc in sorted(RULE_BC.items()) if bc not in ids]
    owned = {rule for rule, domain in defined.items() if bc_of(rule, domain) in built}
    for rule, domain in sorted(defined.items()):
        if rule in cited:
            continue
        if bc_of(rule, domain) is None:
            found.append(f"RT-4 {rule}: uncited and its domain {domain!r} has no BC in DOMAIN_BC. fix: write the rule's domain column "
                         f"(or add the domain to DOMAIN_BC in scripts/guards/rule_tests.py), or add a test that cites it")
        elif rule in owned and rule not in known:
            found.append(f"RT-1 {rule} ({bc_of(rule, domain)}): no test cites it. fix: add a test whose name or @Covers cites {rule}; "
                         f"never add it to {KNOWN}")
    for rule in sorted(set(known)):
        if rule in cited:
            found.append(f"RT-2 {rule}: a test cites it now. fix: delete its line in {KNOWN}")
        elif rule not in owned:
            found.append(f"RT-3 {rule}: not a defined rule of a built BC. fix: delete its line in {KNOWN}")
    if base_known is not None:
        found += [f"RT-5 {rule}: not in {KNOWN} on the base branch; the list only shrinks. fix: add a test that cites {rule} "
                  f"instead of listing it" for rule in sorted(set(known) - set(base_known))]
    return found


def base_list(root, base):
    sha = subprocess.run(["git", "-C", root, "merge-base", base, "HEAD"], capture_output=True, text=True)
    point = sha.stdout.strip() if sha.returncode == 0 else base
    shown = subprocess.run(["git", "-C", root, "show", f"{point}:{KNOWN}"], capture_output=True, text=True)
    return known_ids(shown.stdout) if shown.returncode == 0 else None


def check(root, base=None):
    module = scanner(root)
    tests = module.collect(pathlib.Path(root))[0]
    with open(os.path.join(root, KNOWN), encoding="utf-8") as f:
        known = known_ids(f.read())
    return problems(definitions(root), module.cited_ids(tests), known, built_contexts(root),
                    base_list(root, base) if base else None)


def run_check(base):
    root = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip()
    found = check(root, base)
    for message in found:
        print(message)
    print(f"rule_tests: {len(found)} problem(s)")
    return 1 if found else 0


def self_test():
    ids = {"platform.approval", "organization.workforce", "sales.x"}
    contexts = (ids, {"platform.approval", "organization.workforce"})
    saved, saved_rule = dict(DOMAIN_BC), dict(RULE_BC)
    RULE_BC.clear()
    DOMAIN_BC.clear()
    DOMAIN_BC.update({"approval": "platform.approval", "org": "organization.workforce", "sales": "sales.x"})
    try:
        defined = {"RULE-1": "approval", "RULE-2": "approval", "RULE-3": "sales", "RULE-4": "org", "RULE-5": None}

        def hit(cited, known, base=None, d=defined):
            return sorted({p.split()[0] for p in problems(d, cited, known, contexts, base)})

        assert hit({"RULE-1", "RULE-2", "RULE-4", "RULE-5"}, []) == [], "every rule cited; an unbuilt BC is not checked"
        assert hit({"RULE-1", "RULE-4", "RULE-5"}, []) == ["RT-1"], "(a) a new uncited rule of a built BC"
        assert hit({"RULE-1", "RULE-4", "RULE-5"}, ["RULE-2"]) == [], "an uncited rule on the list passes"
        assert hit({"RULE-1", "RULE-4", "RULE-5"}, ["RULE-2"], ["RULE-9"]) == ["RT-5"], "(b) an id appended to the list"
        assert hit({"RULE-1", "RULE-4", "RULE-5"}, ["RULE-2"], ["RULE-2", "RULE-9"]) == [], "an id removed since the base is fine"
        assert hit({"RULE-1", "RULE-2", "RULE-4", "RULE-5"}, ["RULE-2"]) == ["RT-2"], "(c) a cited id left on the list"
        assert hit({"RULE-1", "RULE-2", "RULE-4", "RULE-5"}, ["RULE-3", "RULE-8"]) == ["RT-3"], "unbuilt BC or unknown id on the list"
        assert hit({"RULE-1", "RULE-2", "RULE-4"}, []) == ["RT-4"], "an uncited rule with no domain"
        assert hit({"RULE-1", "RULE-2", "RULE-4"}, [], d={**defined, "RULE-5": "mystery"}) == ["RT-4"], "an uncited rule with a domain outside DOMAIN_BC"
        DOMAIN_BC["ghost"] = "no.such"
        assert hit({"RULE-1", "RULE-2", "RULE-4", "RULE-5"}, []) == ["RT-4"], "DOMAIN_BC names a BC contexts.yaml lacks"
        del DOMAIN_BC["ghost"]
        RULE_BC["RULE-2"] = "sales.x"
        assert hit({"RULE-1", "RULE-4", "RULE-5"}, []) == [], "an id-level override beats the domain (RULE-2 moved to an unbuilt BC)"
        RULE_BC["RULE-2"] = "no.such"
        assert hit({"RULE-1", "RULE-2", "RULE-4", "RULE-5"}, []) == ["RT-4"], "RULE_BC names a BC contexts.yaml lacks"
        del RULE_BC["RULE-2"]
        assert "never add it to" in problems(defined, {"RULE-1", "RULE-4", "RULE-5"}, [], contexts)[0], "a failure says how to fix it"
    finally:
        DOMAIN_BC.clear()
        DOMAIN_BC.update(saved)
        RULE_BC.clear()
        RULE_BC.update(saved_rule)
    with tempfile.TemporaryDirectory() as tmp:
        def write(rel, text):
            path = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)

        write(f"{RULES}/a.md", "# t\n| RULE-1 | approval | x |\n### RULE-2: y\n## 候选规则：z\n| RULE-9 | approval | no |\n"
                              "## RULE-3 back\n| RULE-4a | org | w |\n| GL-1 | x |\n")
        assert definitions(tmp) == {"RULE-1": "approval", "RULE-2": None, "RULE-3": None, "RULE-4a": "org"}, \
            "table rows and headings define; a candidate section and other ids do not"
        write(CONTEXTS, "contexts:\n- id: a.b\n  status: built\n  note: x\n- id: c.d\n  status: planned\n  evidence: status: built\n")
        assert built_contexts(tmp) == ({"a.b", "c.d"}, {"a.b"}), "built status from the top-level lines only"
        assert known_ids("# h\nRULE-1  # why\n\nRULE-2\n") == ["RULE-1", "RULE-2"], "comments and blanks are free"
        shutil_scan = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../codegen/scan_acceptance_tests.py")
        write("scripts/codegen/scan_acceptance_tests.py", open(shutil_scan, encoding="utf-8").read())
        write("backend/src/test/java/p/ATest.java",
              'package p;\nimport org.junit.jupiter.api.*;\nclass ATest {\n  @Test @DisplayName("RULE-1 works") void a() {}\n}\n')
        os.makedirs(os.path.join(tmp, "frontend"))
        module = scanner(tmp)
        assert module.cited_ids(module.collect(pathlib.Path(tmp))[0]) == {"RULE-1"}, "citations come from the scanner"
        write(KNOWN, "# none\n")
        subprocess.run(["git", "init", "-q", "-b", "main", tmp], check=True)
        for key, value in (("user.email", "t@example.invalid"), ("user.name", "t")):
            subprocess.run(["git", "-C", tmp, "config", key, value], check=True)
        write(KNOWN, "# none\nRULE-2\n")
        subprocess.run(["git", "-C", tmp, "add", "-A"], check=True)
        subprocess.run(["git", "-C", tmp, "commit", "-qm", "base"], check=True)
        assert base_list(tmp, "main") == ["RULE-2"], "the list on the base"
        write(KNOWN, "# none\nRULE-2\nRULE-7\n")
        assert base_list(tmp, "main") == ["RULE-2"], "the base is the commit, not the working tree"
        assert base_list(tmp, "nope") is None, "no base list: nothing to compare"
    print("rule_tests self-test: ok")
    return 0


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "--self-test":
        sys.exit(self_test())
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(run_check(sys.argv[1]))
