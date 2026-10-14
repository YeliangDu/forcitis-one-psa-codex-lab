#!/usr/bin/env python3
"""Refuses a deployment the repository says must not happen yet.

  deploy_gate.py <dev|prod>     run from the repository root on the main checkout
  deploy_gate.py --self-test

- Any environment: a deploy-blocking entry under docs/modernization/status/blockers/ (080-*, or a name containing "no-deploy")
  stops the deployment. The coordinating session deletes the entry when the fix batch is merged.
- prod: every Phase 1a exit condition in section D of PREDEPLOY_CHECKLIST.md must be marked done (the status cell holds the
  done mark). The checklist is the only record of those conditions, so the gate reads it rather than a second list.
"""
import os
import re
import sys
import tempfile

BLOCKERS = "docs/modernization/status/blockers"
CHECKLIST = "docs/modernization/phase1a/infra/PREDEPLOY_CHECKLIST.md"
DONE = "✅"
EXPECTED_EXIT_CONDITIONS = 9


def deploy_blockers(root):
    directory = os.path.join(root, BLOCKERS)
    if not os.path.isdir(directory):
        raise SystemExit(f"deploy_gate.py: {BLOCKERS} is missing; refusing to guess whether deployment is allowed")
    return sorted(name for name in os.listdir(directory) if name.startswith("080-") or "no-deploy" in name)


def exit_conditions(checklist_text):
    """(id, done) for each row of section D; section D is the table after the heading starting with '## D.'"""
    section = re.search(r"^## D\..*?$(.*?)(?=^## |\Z)", checklist_text, flags=re.MULTILINE | re.DOTALL)
    if not section:
        return []
    rows = []
    for line in section.group(1).splitlines():
        match = re.match(r"^\|\s*(D-\d+)\s*\|", line)
        if match:
            status = line.rstrip().rstrip("|").rsplit("|", 1)[-1]
            rows.append((match.group(1), DONE in status))
    return rows


def check(root, environment):
    problems = [f"deployment blocked by {BLOCKERS}/{name}" for name in deploy_blockers(root)]
    if environment == "prod":
        with open(os.path.join(root, CHECKLIST), encoding="utf-8") as handle:
            rows = exit_conditions(handle.read())
        if len(rows) < EXPECTED_EXIT_CONDITIONS:
            problems.append(f"{CHECKLIST} section D lists {len(rows)} exit conditions, expected at least {EXPECTED_EXIT_CONDITIONS}; "
                            "the table changed shape, fix the gate with it")
        open_rows = [row for row, done in rows if not done]
        if open_rows:
            problems.append(f"Phase 1a exit conditions not done in {CHECKLIST}: {', '.join(open_rows)}")
    return problems


def self_test():
    table = "## D. 部署后验证\n\n| # | 出口条件 | 依赖 | 状态 |\n|---|---|---|---|\n"
    with tempfile.TemporaryDirectory() as root:
        os.makedirs(os.path.join(root, BLOCKERS))
        os.makedirs(os.path.dirname(os.path.join(root, CHECKLIST)))

        def checklist(statuses):
            rows = "".join(f"| D-{i} | x | y | {s} |\n" for i, s in enumerate(statuses, 1))
            with open(os.path.join(root, CHECKLIST), "w", encoding="utf-8") as handle:
                handle.write(table + rows + "\n## 不阻塞\n| D-99 | not in D | | ⬜ |\n")

        checklist([DONE] * 9)
        assert check(root, "dev") == [] and check(root, "prod") == []
        checklist([DONE] * 8 + ["⬜"])
        assert check(root, "dev") == [] and "D-9" in check(root, "prod")[0]
        checklist([DONE] * 3)
        assert any("expected at least" in p for p in check(root, "prod"))
        open(os.path.join(root, BLOCKERS, "080-1a-no-deploy-until-fix.md"), "w").close()
        assert check(root, "dev") and check(root, "prod")
        os.remove(os.path.join(root, BLOCKERS, "080-1a-no-deploy-until-fix.md"))
        open(os.path.join(root, BLOCKERS, "120-prod-no-deploy.md"), "w").close()
        assert check(root, "dev")
    with open(CHECKLIST, encoding="utf-8") as handle:
        assert len(exit_conditions(handle.read())) >= EXPECTED_EXIT_CONDITIONS, f"{CHECKLIST} section D no longer parses"
    print("deploy_gate.py self-test passed")


def main(argv):
    if argv == ["--self-test"]:
        self_test()
        return 0
    if len(argv) != 1 or argv[0] not in ("dev", "prod"):
        print(__doc__, file=sys.stderr)
        return 2
    problems = check(".", argv[0])
    for problem in problems:
        print(f"deploy_gate.py: {problem}", file=sys.stderr)
    if not problems:
        print(f"deploy_gate.py: {argv[0]} deployment allowed by the repository state")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
