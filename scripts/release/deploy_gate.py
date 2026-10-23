#!/usr/bin/env python3
"""Refuses a deployment the repository says must not happen yet.

  deploy_gate.py <dev|prod> <version>                        run from the repository root on the main checkout
  deploy_gate.py approval <version> <commit> <bundle sha256>  prod, after the signed statement verified: the approval names exactly this build,
                                                             and that build's commit no longer carries the baseline-open marker
  deploy_gate.py --self-test

- Any environment: a blocker entry under docs/modernization/status/blockers/ whose line carries the marker `blocks: deploy` stops the
  deployment. Blocker fragments are single lines (build_records.py), so the marker sits in the line instead of front matter. The
  coordinating session deletes the entry, or the marker, when the blocking work is merged.
- prod: the migration baseline-open marker (backend/architecture/migration-baseline-open) stops the deployment, both on main and in the
  commit the bundle was built from (a release tagged while the baseline was open stays undeployable to prod); it is removed before the
  first production deployment, in a pull request of its own (SESSION_PLAYBOOK 附 M3「迁移基线」).
- dev: an open baseline is allowed and only warned about. While it is open the baseline files may change after a dev deployment; when they
  did, the dev database and the server's release state are reset first (C-16 of PREDEPLOY_CHECKLIST.md), the installer refuses
  changed migrations otherwise.
- prod: no pre-release version (-rc.N); every Phase 1a exit condition in section D of PREDEPLOY_CHECKLIST.md is done (verified on dev);
  C-9 (the production database) and C-15 (the migration baseline closed and rehearsed, ADR-0038) are done. "Done" means the status cell
  is exactly the done mark, nothing else. The version is listed in prod-approvals.txt as
  "<version> <commit> <bundle sha256> <YYYY-MM-DD> <approver>": on the GitHub Team plan a private repository has no required reviewers, so a reviewed approval line merged into main is the human approval, and it pins the exact build approved.
"""
import io
import os
import re
import subprocess
import sys
import tempfile

BLOCKERS = "docs/modernization/status/blockers"
CHECKLIST = "docs/modernization/phase1a/infra/PREDEPLOY_CHECKLIST.md"
PROD_APPROVALS = "docs/modernization/phase1a/infra/runbooks/prod-approvals.txt"
DEPLOY_MARKER = "`blocks: deploy`"
BASELINE_MARKER = "backend/architecture/migration-baseline-open"
DONE = "✅"
EXPECTED_EXIT_CONDITIONS = 10
PROD_PREREQUISITES = ("C-9", "C-15")


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


def baseline_open(root):
    directory, name = os.path.split(os.path.join(root, BASELINE_MARKER))
    return os.path.isdir(directory) and any(entry.lower() == name.lower() for entry in os.listdir(directory))


def baseline_open_at(root, commit):
    directory, name = os.path.split(BASELINE_MARKER)
    listing = subprocess.run(["git", "-C", root, "ls-tree", "--name-only", commit, f"{directory}/"], capture_output=True, text=True)
    if listing.returncode != 0:
        raise SystemExit(f"deploy_gate.py: cannot read {commit}; refusing to guess whether its baseline was open")
    return any(os.path.basename(entry).lower() == name.lower() for entry in listing.stdout.splitlines())


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


def approvals(root):
    """{version: (commit, bundle sha256)} from prod-approvals.txt; a malformed or repeated line fails the gate."""
    with open(os.path.join(root, PROD_APPROVALS), encoding="utf-8") as handle:
        rows = [line.split() for line in handle if line.strip() and not line.startswith("#")]
    found = {}
    for row in rows:
        if (len(row) < 5 or not re.fullmatch(r"[0-9a-f]{40}", row[1]) or not re.fullmatch(r"[0-9a-f]{64}", row[2])
                or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", row[3])):
            raise SystemExit(f"deploy_gate.py: {PROD_APPROVALS} line {' '.join(row)!r} is not "
                             "'<version> <commit> <bundle sha256> <YYYY-MM-DD> <approver>'")
        if row[0] in found:
            raise SystemExit(f"deploy_gate.py: {PROD_APPROVALS} approves {row[0]} twice")
        found[row[0]] = (row[1], row[2])
    return found


def approval_problems(root, version, commit, bundle_sha256):
    approved = approvals(root).get(version)
    if approved is None:
        return [f"{version} is not approved for prod in {PROD_APPROVALS}"]
    if approved != (commit, bundle_sha256):
        return [f"{PROD_APPROVALS} approves {version} as commit {approved[0]} bundle {approved[1]}, not {commit} bundle {bundle_sha256}"]
    if baseline_open_at(root, commit):
        return [f"{version} was built from {commit}, which still carries {BASELINE_MARKER}; prod takes builds made after the baseline closed"]
    return []


def dev_warnings(root, environment):
    if environment == "dev" and baseline_open(root):
        return [f"{BASELINE_MARKER} exists: the migration baseline is still open; if any baseline migration changed since the last dev "
                f"deployment, reset the dev database and the server's release state first (dev database rebuild runbook, {CHECKLIST} C-16), "
                "the installer refuses changed migrations otherwise"]
    return []


def check(root, environment, version):
    problems = [f"deployment blocked by {BLOCKERS}/{name}" for name in deploy_blockers(root)]
    if environment == "prod":
        if baseline_open(root):
            problems.append(f"{BASELINE_MARKER} exists: the migration baseline is still open; prod needs it closed, remove the marker first "
                            "(SESSION_PLAYBOOK 附 M3「迁移基线」)")
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
        if version not in approvals(root):
            problems.append(f"{version} is not approved for prod in {PROD_APPROVALS}")
        executions = table_rows(text, "C")
        for row in PROD_PREREQUISITES:
            if executions.get(row) != DONE:
                problems.append(f"{row} in {CHECKLIST} is not done ({executions.get(row, 'row missing')})")
    return problems


def self_test():
    with tempfile.TemporaryDirectory() as root:
        os.makedirs(os.path.join(root, BLOCKERS))
        os.makedirs(os.path.dirname(os.path.join(root, PROD_APPROVALS)))
        def git(*args):
            return subprocess.run(["git", "-C", root, "-c", "user.name=self-test", "-c", "user.email=self-test@invalid",
                                   "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", *args], check=True, capture_output=True, text=True).stdout.strip()

        git("init", "-q")
        marker = os.path.join(root, BASELINE_MARKER)
        os.makedirs(os.path.dirname(marker))
        with open(marker, "w", encoding="utf-8") as handle:
            handle.write("open\n")
        git("add", "-A")
        git("commit", "-q", "-m", "open")
        open_commit = git("rev-parse", "HEAD")
        os.remove(marker)
        git("commit", "-q", "-a", "-m", "closed")
        closed_commit = git("rev-parse", "HEAD")
        with open(os.path.join(root, PROD_APPROVALS), "w", encoding="utf-8") as handle:
            handle.write(f"# header\n0.1.0 {closed_commit} {'d' * 64} 2026-10-14 平台负责人\n"
                         f"0.0.9 {open_commit} {'d' * 64} 2026-10-14 平台负责人\n")

        def checklist(statuses, c9=DONE, c15=DONE):
            rows = "".join(f"| D-{i} | x | y | {s} |\n" for i, s in enumerate(statuses, 1))
            with open(os.path.join(root, CHECKLIST), "w", encoding="utf-8") as handle:
                handle.write(f"## C. 执行\n\n| # | 事项 | 状态 |\n|---|---|---|\n| C-9 | 生产库 | {c9} |\n| C-15 | 基线关闭 | {c15} |\n\n"
                             f"## D. 部署后验证\n\n| # | 出口条件 | 依赖 | 状态 |\n|---|---|---|---|\n{rows}\n"
                             "## 不阻塞\n| D-99 | not in D | | ⬜ |\n")

        def blocker(name, text):
            with open(os.path.join(root, BLOCKERS, name), "w", encoding="utf-8") as handle:
                handle.write(text)

        checklist([DONE] * EXPECTED_EXIT_CONDITIONS)
        assert check(root, "dev", "0.1.0-rc.1") == [] and check(root, "prod", "0.1.0") == []
        with open(marker, "w", encoding="utf-8") as handle:
            handle.write("open\n")
        assert check(root, "dev", "0.1.0-rc.1") == [], "an open migration baseline does not stop dev"
        assert any("baseline is still open" in w for w in dev_warnings(root, "dev")), "an open migration baseline is warned about on dev"
        assert any("baseline is still open" in p for p in check(root, "prod", "0.1.0")), "an open migration baseline stops prod"
        assert dev_warnings(root, "prod") == [], "prod reports the open baseline as a problem, not a warning"
        streams, os.environ["GITHUB_ACTIONS"] = (sys.stdout, sys.stderr), "true"
        try:
            sys.stdout, sys.stderr = io.StringIO(), (captured := io.StringIO())
            assert main(["dev", "0.1.0-rc.1"], root) == 0, "dev with an open baseline deploys"
        finally:
            sys.stdout, sys.stderr = streams
            del os.environ["GITHUB_ACTIONS"]
        assert captured.getvalue().startswith("::warning::deploy_gate.py: "), "the warning is an Actions annotation"
        os.rename(marker, marker.replace("migration-baseline-open", "Migration-Baseline-Open"))
        assert check(root, "prod", "0.1.0"), "a marker differing only in case still stops prod"
        assert dev_warnings(root, "dev"), "a marker differing only in case is still warned about on dev"
        os.remove(marker.replace("migration-baseline-open", "Migration-Baseline-Open"))
        assert check(root, "prod", "0.1.0") == [] and dev_warnings(root, "dev") == [], "removing the marker lifts the stop and the warning"
        assert check(root, "prod", "0.1.0-rc.1"), "prod refuses pre-releases"
        assert any("not approved" in p for p in check(root, "prod", "0.2.0")), "prod needs an approval line"
        assert approval_problems(root, "0.1.0", closed_commit, "d" * 64) == []
        assert approval_problems(root, "0.1.0", "e" * 40, "d" * 64), "the approval pins the commit"
        assert approval_problems(root, "0.1.0", closed_commit, "f" * 64), "the approval pins the bundle"
        assert any("still carries" in p for p in approval_problems(root, "0.0.9", open_commit, "d" * 64)), \
            "a build made while the baseline was open never reaches prod, approved or not"
        try:
            baseline_open_at(root, "e" * 40)
            raise AssertionError("an unknown commit must fail, not pass")
        except SystemExit:
            pass
        checklist([DONE] * (EXPECTED_EXIT_CONDITIONS - 1) + ["⬜"])
        assert check(root, "dev", "0.1.0") == [] and f"D-{EXPECTED_EXIT_CONDITIONS}" in check(root, "prod", "0.1.0")[0]
        checklist([DONE] * (EXPECTED_EXIT_CONDITIONS - 1) + [DONE + " 部分"])
        assert check(root, "prod", "0.1.0"), "a done mark with extra text is not done"
        checklist([DONE] * EXPECTED_EXIT_CONDITIONS, c9="\U0001f7e8")
        assert any("C-9" in p for p in check(root, "prod", "0.1.0"))
        checklist([DONE] * EXPECTED_EXIT_CONDITIONS, c15="⬜")
        assert any("C-15" in p for p in check(root, "prod", "0.1.0")), "prod waits for the baseline to be closed and rehearsed (C-15)"
        assert check(root, "dev", "0.1.0-rc.1") == [], "C-15 does not hold back dev"
        checklist([DONE] * 3)
        assert any("expected at least" in p for p in check(root, "prod", "0.1.0"))
        checklist([DONE] * EXPECTED_EXIT_CONDITIONS)
        blocker("070-other.md", "- unrelated work\n")
        assert check(root, "dev", "0.1.0") == [], "a blocker without the marker does not stop deployment"
        blocker("080-fix.md", f"- no deployment until the fix batch is merged ({DEPLOY_MARKER})\n")
        assert check(root, "dev", "0.1.0") and check(root, "prod", "0.1.0")
    with open(CHECKLIST, encoding="utf-8") as handle:
        text = handle.read()
    assert len(table_rows(text, "D")) >= EXPECTED_EXIT_CONDITIONS, f"{CHECKLIST} section D no longer parses"
    assert "D-10" in table_rows(text, "D"), f"{CHECKLIST} section D lacks D-10 (blocker 090 before prod)"
    assert all(row in table_rows(text, "C") for row in PROD_PREREQUISITES), f"{CHECKLIST} section C lacks {PROD_PREREQUISITES}"
    approvals(".")
    print("deploy_gate.py self-test passed")


def main(argv, root="."):
    if argv == ["--self-test"]:
        self_test()
        return 0
    if len(argv) == 4 and argv[0] == "approval":
        problems = approval_problems(root, *argv[1:])
        for problem in problems:
            print(f"deploy_gate.py: {problem}", file=sys.stderr)
        if not problems:
            print(f"deploy_gate.py: {argv[1]} is approved for prod as exactly this build")
        return 1 if problems else 0
    if len(argv) != 2 or argv[0] not in ("dev", "prod"):
        print(__doc__, file=sys.stderr)
        return 2
    for warning in dev_warnings(root, argv[0]):
        print(f"::warning::deploy_gate.py: {warning}" if os.environ.get("GITHUB_ACTIONS") == "true" else f"deploy_gate.py: warning: {warning}",
              file=sys.stderr)
    problems = check(root, argv[0], argv[1])
    for problem in problems:
        print(f"deploy_gate.py: {problem}", file=sys.stderr)
    if not problems:
        print(f"deploy_gate.py: deploying {argv[1]} to {argv[0]} is allowed by the repository state")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
