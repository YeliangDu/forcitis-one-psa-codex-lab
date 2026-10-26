#!/usr/bin/env python3
"""Refuses a deployment the repository says must not happen yet.

  deploy_gate.py <dev|prod> <version>                        run from the repository root on the main checkout
  deploy_gate.py approval <version> <commit> <bundle sha256>  prod, after the signed statement verified: the approval names exactly this build,
                                                             and that build's commit no longer carries the baseline-open marker
  deploy_gate.py --render                                    write the status cells of PREDEPLOY_CHECKLIST.md section D, C-9 and C-15 from the gate file
  deploy_gate.py --self-test

The repository's say is one machine-readable file, docs/modernization/phase1a/infra/deploy_gate.json (K02, GOV-1023-FV2): blockers, the
section D exit conditions, C-9 and C-15. The checklist's cells for them are a view written by --render; the self-test fails when they differ.
A missing or malformed gate file refuses every deployment.
- Any environment: every entry in the gate file's blockers stops the deployment. The coordinating session deletes the entry when the
  blocking work is merged.
- prod: the migration baseline-open marker (backend/architecture/migration-baseline-open) stops the deployment, both on main and in the
  commit the bundle was built from (a release tagged while the baseline was open stays undeployable to prod); it is removed before the
  first production deployment, in a pull request of its own (SESSION_PLAYBOOK 附 M3「迁移基线」).
- dev: an open baseline is allowed and only warned about. While it is open the baseline files may change after a dev deployment; when they
  did, the dev database and the server's release state are reset first (C-16 of PREDEPLOY_CHECKLIST.md), the installer refuses
  changed migrations otherwise.
- prod: no pre-release version (-rc.N); every Phase 1a exit condition (section D, verified on dev) is done; C-9 (the production database)
  and C-15 (the migration baseline closed and rehearsed, ADR-0038) are done. "Done" means the status in the gate file is exactly the done
  mark, nothing else. The version is listed in prod-approvals.txt as
  "<version> <commit> <bundle sha256> <YYYY-MM-DD> <approver>": on the GitHub Team plan a private repository has no required reviewers, so a reviewed approval line merged into main is the human approval, and it pins the exact build approved.
"""
import io
import json
import os
import re
import subprocess
import sys
import tempfile

GATE_FILE = "docs/modernization/phase1a/infra/deploy_gate.json"
CHECKLIST = "docs/modernization/phase1a/infra/PREDEPLOY_CHECKLIST.md"
PROD_APPROVALS = "docs/modernization/phase1a/infra/runbooks/prod-approvals.txt"
BASELINE_MARKER = "backend/architecture/migration-baseline-open"
DONE = "✅"
EXPECTED_EXIT_CONDITIONS = 10
PROD_PREREQUISITES = ("C-9", "C-15")


def gate_file(root):
    """The gate file, checked: blockers a list of {id, reason}; exit_conditions {D-n: status}, at least EXPECTED_EXIT_CONDITIONS of them;
    prod_prerequisites {C-n: status} naming PROD_PREREQUISITES. Anything else refuses every deployment."""
    try:
        with open(os.path.join(root, GATE_FILE), encoding="utf-8") as handle:
            data = json.load(handle)
        blockers, exits, prerequisites = data["blockers"], data["exit_conditions"], data["prod_prerequisites"]
        ok = (isinstance(blockers, list) and all(isinstance(b, dict) and isinstance(b.get("id"), str) and b["id"] and isinstance(b.get("reason"), str) for b in blockers)
              and isinstance(exits, dict) and isinstance(prerequisites, dict)
              and all(re.fullmatch(r"D-\d+", k) and isinstance(v, str) for k, v in exits.items())
              and all(re.fullmatch(r"C-\d+", k) and isinstance(v, str) for k, v in prerequisites.items()))
    except (OSError, ValueError, KeyError, TypeError):
        ok = False
    if not ok:
        raise SystemExit(f"deploy_gate.py: {GATE_FILE} is missing or malformed; refusing to guess whether deployment is allowed")
    return data


def baseline_open(root):
    directory, name = os.path.split(os.path.join(root, BASELINE_MARKER))
    return os.path.isdir(directory) and any(entry.lower() == name.lower() for entry in os.listdir(directory))


def baseline_open_at(root, commit):
    directory, name = os.path.split(BASELINE_MARKER)
    listing = subprocess.run(["git", "-C", root, "ls-tree", "--name-only", commit, f"{directory}/"], capture_output=True, text=True)
    if listing.returncode != 0:
        raise SystemExit(f"deploy_gate.py: cannot read {commit}; refusing to guess whether its baseline was open")
    return any(os.path.basename(entry).lower() == name.lower() for entry in listing.stdout.splitlines())


def render(checklist_text, data):
    """The checklist with the last cell of each section D row and of C-9, C-15 (in section C) replaced by the gate file's status. Every id
    the file names must be written exactly once, and section D must hold no other row; anything else raises instead of returning the text."""
    wanted = {"D": dict(data["exit_conditions"]), "C": dict(data["prod_prerequisites"])}
    if set(table_rows(checklist_text, "D")) != set(wanted["D"]):
        raise SystemExit(f"deploy_gate.py: {CHECKLIST} section D rows differ from {GATE_FILE}; add or remove the row in both")
    hits, section, out = {}, None, []
    for line in checklist_text.splitlines(keepends=True):
        heading = re.match(r"^## ([A-Z])\.", line)
        section = heading.group(1) if heading else section if not line.startswith("## ") else None
        row = re.match(r"^\|\s*((?:C|D)-\d+)\s*\|", line)
        if row and section in wanted and row.group(1).startswith(section) and row.group(1) in wanted[section]:
            ending = line[len(line.rstrip("\r\n")):]
            body = line.rstrip("\r\n").rstrip(" \t")
            closed = body.endswith("|")
            body = body[:-1] if closed else body
            line = body[:body.rindex("|") + 1] + f" {wanted[section][row.group(1)]} " + ("|" if closed else "") + ending
            hits[row.group(1)] = hits.get(row.group(1), 0) + 1
        out.append(line)
    missing = [k for part in wanted.values() for k in part if hits.get(k) != 1]
    if missing:
        raise SystemExit(f"deploy_gate.py: {CHECKLIST} has no single row to write for {', '.join(missing)}; fix the table")
    return "".join(out)


def check_view(root):  # Refuses (SystemExit) when the checklist's cells differ from the gate file: the table is a view written by --render.
    with open(os.path.join(root, CHECKLIST), encoding="utf-8") as handle:
        text = handle.read()
    if not consistent(text, gate_file(root)):
        raise SystemExit(f"deploy_gate.py: {CHECKLIST} differs from {GATE_FILE}: run deploy_gate.py --render (the table is a view of the gate file)")


def consistent(checklist_text, data):  # The checklist's cells, as table_rows reads them, equal the gate file's status for every id, each id on exactly one row.
    try:
        render(checklist_text, data)
    except SystemExit:
        return False
    rows = table_rows(checklist_text, "D")
    executions = table_rows(checklist_text, "C")
    return rows == dict(data["exit_conditions"]) and all(executions.get(k) == v for k, v in data["prod_prerequisites"].items())


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
    data = gate_file(root)
    problems = [f"deployment blocked by {GATE_FILE} blocker {b['id']}: {b['reason']}" for b in data["blockers"]]
    if environment == "prod":
        if baseline_open(root):
            problems.append(f"{BASELINE_MARKER} exists: the migration baseline is still open; prod needs it closed, remove the marker first "
                            "(SESSION_PLAYBOOK 附 M3「迁移基线」)")
        if "-" in version:
            problems.append(f"prod takes released versions only, not the pre-release {version}")
        exits = data["exit_conditions"]
        if len(exits) < EXPECTED_EXIT_CONDITIONS:
            problems.append(f"{GATE_FILE} lists {len(exits)} exit conditions, expected at least {EXPECTED_EXIT_CONDITIONS}; "
                            "the list changed shape, fix the gate with it")
        open_exits = [row for row, status in exits.items() if status != DONE]
        if open_exits:
            problems.append(f"Phase 1a exit conditions (verified on dev) not done in {GATE_FILE}: {', '.join(open_exits)}")
        if version not in approvals(root):
            problems.append(f"{version} is not approved for prod in {PROD_APPROVALS}")
        for row in PROD_PREREQUISITES:
            if data["prod_prerequisites"].get(row) != DONE:
                problems.append(f"{row} in {GATE_FILE} is not done ({data['prod_prerequisites'].get(row, 'row missing')})")
    return problems


def self_test():
    with tempfile.TemporaryDirectory() as root:
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

        blockers = []

        def checklist(statuses, c9=DONE, c15=DONE, table=DONE):  # the gate file says `statuses`; the checklist's cells say `table` (a view only)
            rows = "".join(f"| D-{i} | x | y | {table} |\n" for i in range(1, len(statuses) + 1))
            with open(os.path.join(root, CHECKLIST), "w", encoding="utf-8") as handle:
                handle.write(f"## C. 执行\n\n| # | 事项 | 状态 |\n|---|---|---|\n| C-9 | 生产库 | {table} |\n| C-15 | 基线关闭 | {table} |\n\n"
                             f"## D. 部署后验证\n\n| # | 出口条件 | 依赖 | 状态 |\n|---|---|---|---|\n{rows}\n"
                             "## 不阻塞\n| D-99 | not in D | | ⬜ |\n")
            with open(os.path.join(root, GATE_FILE), "w", encoding="utf-8") as handle:
                json.dump({"blockers": blockers, "exit_conditions": {f"D-{i}": s for i, s in enumerate(statuses, 1)},
                           "prod_prerequisites": {"C-9": c9, "C-15": c15}}, handle, ensure_ascii=False)

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
        checklist(["⬜"] * EXPECTED_EXIT_CONDITIONS, c9="⬜", c15="⬜", table=DONE)
        assert len(check(root, "prod", "0.1.0")) == 3, "the checklist's cells are a view: all ✅ there, open in the gate file, prod refused"
        with open(os.path.join(root, CHECKLIST), encoding="utf-8") as handle:
            rendered = render(handle.read(), gate_file(root))
        assert set(table_rows(rendered, "D").values()) == {"⬜"} and table_rows(rendered, "C") == {"C-9": "⬜", "C-15": "⬜"} \
            and "| D-99 | not in D | | ⬜ |" in rendered, "--render writes the gate file's status into section D, C-9 and C-15 only"
        assert consistent(rendered, gate_file(root)) and not consistent(rendered.replace("| D-3 | x | y | ⬜ |", "| D-3 | x | y | ✅ |"), gate_file(root)), \
            "a cell differing from the gate file is inconsistent"
        for variant in (rendered.replace("| D-3 | x | y | ⬜ |", "| D-3 | x | y | ✅"), rendered.replace("\n", "\r\n").replace("| C-9 | 生产库 | ⬜ |", "| C-9 | 生产库 | ✅ |")):
            assert not consistent(variant, gate_file(root)), "no closing pipe or CRLF line endings do not hide a difference"
            assert consistent(render(variant, gate_file(root)), gate_file(root)), "--render still writes such a row"
        with open(os.path.join(root, CHECKLIST), "w", encoding="utf-8") as handle:
            handle.write(rendered.replace("| D-3 | x | y | ⬜ |", "| D-3 | x | y | ✅ |"))
        try:
            check_view(root)
            raise AssertionError("a checklist differing from the gate file must fail the view check")
        except SystemExit:
            pass
        for broken in (rendered.replace("| D-3 | x | y | ⬜ |\n", ""), rendered.replace("| C-9 | 生产库 | ⬜ |\n", ""), rendered.replace("| D-3 | x | y | ⬜ |\n", "| D-3 | x | y | ⬜ |\n| D-11 | x | y | ⬜ |\n"),
                       rendered.replace("| D-3 | x | y | ⬜ |\n", "| D-3 | x | y | ⬜ |\n| D-3 | x | y | ✅ |\n"), rendered.replace("| C-9 | 生产库 | ⬜ |\n", "| C-9 | 生产库 | ⬜ |\n| C-9 | 生产库 | ⬜ |\n")):
            assert not consistent(broken, gate_file(root)), "a row missing from the table, or one the gate file lacks, is inconsistent"
            try:
                render(broken, gate_file(root))
                raise AssertionError("--render must refuse a table lacking a row, not return it")
            except SystemExit:
                pass
        checklist([DONE] * EXPECTED_EXIT_CONDITIONS)
        blockers.append({"id": "080-fix", "reason": "no deployment until the fix batch is merged"})
        checklist([DONE] * EXPECTED_EXIT_CONDITIONS)
        assert check(root, "dev", "0.1.0") and check(root, "prod", "0.1.0"), "a blocker stops dev and prod"
        for broken in ("", "{", '{"blockers": [], "exit_conditions": {}}', '{"blockers": [{"id": ""}], "exit_conditions": {}, "prod_prerequisites": {}}',
                       '{"blockers": "x", "exit_conditions": {}, "prod_prerequisites": {}}'):
            with open(os.path.join(root, GATE_FILE), "w", encoding="utf-8") as handle:
                handle.write(broken)
            try:
                check(root, "dev", "0.1.0")
                raise AssertionError(f"a malformed gate file must refuse, not pass: {broken!r}")
            except SystemExit:
                pass
        os.remove(os.path.join(root, GATE_FILE))
        try:
            check(root, "dev", "0.1.0")
            raise AssertionError("a missing gate file must refuse, not pass")
        except SystemExit:
            pass
    data = gate_file(".")
    assert len(data["exit_conditions"]) >= EXPECTED_EXIT_CONDITIONS and "D-10" in data["exit_conditions"], f"{GATE_FILE} lacks D-10 (blocker 090 before prod)"
    assert all(row in data["prod_prerequisites"] for row in PROD_PREREQUISITES), f"{GATE_FILE} lacks {PROD_PREREQUISITES}"
    check_view(".")
    approvals(".")
    print("deploy_gate.py self-test passed")


def main(argv, root="."):
    if argv == ["--self-test"]:
        self_test()
        return 0
    if argv == ["--render"]:
        with open(os.path.join(root, CHECKLIST), encoding="utf-8") as handle:
            text = render(handle.read(), gate_file(root))
        with open(os.path.join(root, CHECKLIST), "w", encoding="utf-8") as handle:
            handle.write(text)
        print(f"deploy_gate.py: {CHECKLIST} status cells written from {GATE_FILE}")
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
