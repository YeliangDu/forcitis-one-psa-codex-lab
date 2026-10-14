#!/usr/bin/env python3
"""Refuses a deployment the repository says must not happen yet.

  deploy_gate.py <dev|prod> <version>     run from the repository root on the main checkout
  deploy_gate.py --self-test

- Any environment: a blocker entry under docs/modernization/status/blockers/ whose line carries the marker `blocks: deploy` stops the
  deployment. Blocker fragments are single lines (build_records.py), so the marker sits in the line instead of front matter. The
  coordinating session deletes the entry, or the marker, when the blocking work is merged.
- prod: no pre-release version (-rc.N); every Phase 1a exit condition in section D of PREDEPLOY_CHECKLIST.md is done (verified on dev);
  C-9 (the production database) is done. "Done" means the status cell is exactly the done mark, nothing else.
"""
import os
import re
import sys
import tempfile

BLOCKERS = "docs/modernization/status/blockers"
CHECKLIST = "docs/modernization/phase1a/infra/PREDEPLOY_CHECKLIST.md"
DEPLOY_MARKER = "`blocks: deploy`"
DONE = "✅"
EXPECTED_EXIT_CONDITIONS = 9
PROD_PREREQUISITES = ("C-9",)


def deploy_blockers(root):
    directory = os.path.join(root, BLOCKERS)
    if not os.path.isdir(directory):
        raise SystemExit(f"deploy_gate.py: {BLOCKERS} is missing; refusing to guess whether deployment is allowed")
    found = []
    for name in sorted(os.listdir(directory)):
        if name.endswith(".md"):
            with open(os.path.join(directory, name), encoding="utf-8") as handle:
                if DEPLOY_MARKER in handle.read():
                    found.append(name)
    return found


def table_rows(checklist_text, section_letter):
    """{id: status cell} for the rows of the table in section '## <letter>.'"""
    section = re.search(rf"^## {section_letter}\..*?$(.*?)(?=^## |\Z)", checklist_text, flags=re.MULTILINE | re.DOTALL)
    rows = {}
    if section:
        for line in section.group(1).splitlines():
            match = re.match(rf"^\|\s*({section_letter}-\d+)\s*\|", line)
            if match:
                rows[match.group(1)] = line.strip().strip("|").split("|")[-1].strip()
    return rows


def check(root, environment, version):
    problems = [f"deployment blocked by {BLOCKERS}/{name}" for name in deploy_blockers(root)]
    if environment == "prod":
        if "-" in version:
            problems.append(f"prod takes released versions only, not the pre-release {version}")
        with open(os.path.join(root, CHECKLIST), encoding="utf-8") as handle:
            text = handle.read()
        exits = table_rows(text, "D")
        if len(exits) < EXPECTED_EXIT_CONDITIONS:
            problems.append(f"{CHECKLIST} section D lists {len(exits)} exit conditions, expected at least {EXPECTED_EXIT_CONDITIONS}; "
                            "the table changed shape, fix the gate with it")
        open_exits = [row for row, status in exits.items() if status != DONE]
        if open_exits:
            problems.append(f"Phase 1a exit conditions (verified on dev) not done in {CHECKLIST}: {', '.join(open_exits)}")
        executions = table_rows(text, "C")
        for row in PROD_PREREQUISITES:
            if executions.get(row) != DONE:
                problems.append(f"{row} in {CHECKLIST} is not done ({executions.get(row, 'row missing')})")
    return problems


def self_test():
    with tempfile.TemporaryDirectory() as root:
        os.makedirs(os.path.join(root, BLOCKERS))
        os.makedirs(os.path.dirname(os.path.join(root, CHECKLIST)))

        def checklist(statuses, c9=DONE):
            rows = "".join(f"| D-{i} | x | y | {s} |\n" for i, s in enumerate(statuses, 1))
            with open(os.path.join(root, CHECKLIST), "w", encoding="utf-8") as handle:
                handle.write(f"## C. 执行\n\n| # | 事项 | 状态 |\n|---|---|---|\n| C-9 | 生产库 | {c9} |\n\n"
                             f"## D. 部署后验证\n\n| # | 出口条件 | 依赖 | 状态 |\n|---|---|---|---|\n{rows}\n"
                             "## 不阻塞\n| D-99 | not in D | | ⬜ |\n")

        def blocker(name, text):
            with open(os.path.join(root, BLOCKERS, name), "w", encoding="utf-8") as handle:
                handle.write(text)

        checklist([DONE] * 9)
        assert check(root, "dev", "0.1.0-rc.1") == [] and check(root, "prod", "0.1.0") == []
        assert check(root, "prod", "0.1.0-rc.1"), "prod refuses pre-releases"
        checklist([DONE] * 8 + ["⬜"])
        assert check(root, "dev", "0.1.0") == [] and "D-9" in check(root, "prod", "0.1.0")[0]
        checklist([DONE] * 8 + [DONE + " 部分"])
        assert check(root, "prod", "0.1.0"), "a done mark with extra text is not done"
        checklist([DONE] * 9, c9="\U0001f7e8")
        assert any("C-9" in p for p in check(root, "prod", "0.1.0"))
        checklist([DONE] * 3)
        assert any("expected at least" in p for p in check(root, "prod", "0.1.0"))
        checklist([DONE] * 9)
        blocker("070-other.md", "- unrelated work\n")
        assert check(root, "dev", "0.1.0") == [], "a blocker without the marker does not stop deployment"
        blocker("080-fix.md", f"- no deployment until the fix batch is merged ({DEPLOY_MARKER})\n")
        assert check(root, "dev", "0.1.0") and check(root, "prod", "0.1.0")
    with open(CHECKLIST, encoding="utf-8") as handle:
        text = handle.read()
    assert len(table_rows(text, "D")) >= EXPECTED_EXIT_CONDITIONS, f"{CHECKLIST} section D no longer parses"
    assert all(row in table_rows(text, "C") for row in PROD_PREREQUISITES), f"{CHECKLIST} section C lacks {PROD_PREREQUISITES}"
    print("deploy_gate.py self-test passed")


def main(argv):
    if argv == ["--self-test"]:
        self_test()
        return 0
    if len(argv) != 2 or argv[0] not in ("dev", "prod"):
        print(__doc__, file=sys.stderr)
        return 2
    problems = check(".", argv[0], argv[1])
    for problem in problems:
        print(f"deploy_gate.py: {problem}", file=sys.stderr)
    if not problems:
        print(f"deploy_gate.py: deploying {argv[1]} to {argv[0]} is allowed by the repository state")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
