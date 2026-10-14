#!/usr/bin/env python3
"""Proves a commit may be released: it is on origin/main and the three required ci checks succeeded on it.

  ci_passed.py <sha>          # needs gh authenticated (GH_TOKEN) and GITHUB_REPOSITORY, run inside a checkout with origin/main fetched
  ci_passed.py --self-test

The required checks are the job names of .github/workflows/ci.yml below; the script fails if ci.yml no longer declares them, so a
renamed job cannot silently drop out of the proof.
"""
import json
import os
import re
import subprocess
import sys

REQUIRED_CHECKS = (
    "backend verify (Modulith + ArchUnit guardrails + integration tests)",
    "frontend verify (typecheck + lint guardrails F1-F14 + structure + build)",
    "secret scan (gitleaks)",
)
CI_WORKFLOW = ".github/workflows/ci.yml"


def declared_job_names(workflow_text):
    return set(re.findall(r"^    name: (.+)$", workflow_text, flags=re.MULTILINE))


def verdict(check_runs):
    """Missing names and names whose latest run did not succeed, from the check-runs API payload."""
    latest = {}
    for run in check_runs:
        if run.get("app", {}).get("slug") != "github-actions":
            continue
        name = run["name"]
        if name not in latest or run.get("started_at", "") > latest[name].get("started_at", ""):
            latest[name] = run
    missing = [n for n in REQUIRED_CHECKS if n not in latest]
    failed = [f"{n} ({latest[n].get('status')}/{latest[n].get('conclusion')})" for n in REQUIRED_CHECKS
              if n in latest and not (latest[n].get("status") == "completed" and latest[n].get("conclusion") == "success")]
    return missing, failed


def run(*command):
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def main(sha):
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        print(f"ci_passed.py: '{sha}' is not a full commit sha", file=sys.stderr)
        return 1
    with open(CI_WORKFLOW, encoding="utf-8") as handle:
        declared = declared_job_names(handle.read())
    undeclared = [n for n in REQUIRED_CHECKS if n not in declared]
    if undeclared:
        print(f"ci_passed.py: {CI_WORKFLOW} no longer declares {undeclared}; update REQUIRED_CHECKS with the branch protection", file=sys.stderr)
        return 1
    if subprocess.run(["git", "merge-base", "--is-ancestor", sha, "origin/main"]).returncode != 0:
        print(f"ci_passed.py: {sha} is not on origin/main; releases come from main only", file=sys.stderr)
        return 1
    repository = os.environ["GITHUB_REPOSITORY"]
    pages = run("gh", "api", "--paginate", "--slurp", f"repos/{repository}/commits/{sha}/check-runs?per_page=100")
    check_runs = [r for page in json.loads(pages) for r in page["check_runs"]]
    missing, failed = verdict(check_runs)
    if missing or failed:
        print(f"ci_passed.py: {sha} lacks successful ci checks; missing {missing}, not successful {failed}", file=sys.stderr)
        return 1
    print(f"ci_passed.py: {sha} is on main and passed: {', '.join(REQUIRED_CHECKS)}")
    return 0


def self_test():
    ok = [{"name": n, "status": "completed", "conclusion": "success", "started_at": "2026-10-14T01:00:00Z",
           "app": {"slug": "github-actions"}} for n in REQUIRED_CHECKS]
    assert verdict(ok) == ([], [])
    assert verdict(ok[1:])[0] == [REQUIRED_CHECKS[0]]
    rerun_failed = ok + [dict(ok[0], conclusion="failure", started_at="2026-10-14T02:00:00Z")]
    assert verdict(rerun_failed)[1], "a later failed re-run must win"
    skipped = [dict(ok[0], conclusion="skipped")] + ok[1:]
    assert verdict(skipped)[1], "skipped is not success"
    foreign = [dict(r, app={"slug": "someone-else"}) for r in ok]
    assert verdict(foreign)[0] == list(REQUIRED_CHECKS), "checks from other apps do not count"
    with open(CI_WORKFLOW, encoding="utf-8") as handle:
        names = declared_job_names(handle.read())
    assert set(REQUIRED_CHECKS) <= names, f"{CI_WORKFLOW} job names drifted: {sorted(names)}"
    print("ci_passed.py self-test passed")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
