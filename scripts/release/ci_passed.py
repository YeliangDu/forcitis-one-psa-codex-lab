#!/usr/bin/env python3
"""Proves a commit may be released: it is a first-parent commit of origin/main (a merge or direct commit on main, never a commit from
inside a merged branch) and the ci workflow, run by the push to main for exactly that commit, succeeded with the three required jobs.

  ci_passed.py <sha>                 # needs gh authenticated (GH_TOKEN), GITHUB_REPOSITORY, and a checkout with origin/main history
  ci_passed.py --tag-commit <tag>    # prints the commit a tag points at, read through the API (works without git credentials)
  ci_passed.py --self-test

The required jobs are the job names of .github/workflows/ci.yml below; the script fails if ci.yml no longer declares them.
"""
import json
import os
import re
import subprocess
import sys

REQUIRED_JOBS = (
    "backend verify (Modulith + ArchUnit guardrails + integration tests)",
    "frontend verify (typecheck + lint guardrails F1-F14 + structure + build)",
    "secret scan (gitleaks)",
)
CI_WORKFLOW = ".github/workflows/ci.yml"
SHA = re.compile(r"[0-9a-f]{40}")


def declared_job_names(workflow_text):
    return set(re.findall(r"^    name: (.+)$", workflow_text, flags=re.MULTILINE))


def on_first_parent(sha, first_parent_shas):
    return sha in first_parent_shas


def ci_run(runs, sha):
    """The latest ci run for sha triggered by a push to main, or None."""
    candidates = [r for r in runs
                  if r.get("head_sha") == sha and r.get("event") == "push" and r.get("head_branch") == "main"
                  and r.get("path") == CI_WORKFLOW]
    return max(candidates, key=lambda r: (r.get("run_number", 0), r.get("run_attempt", 0)), default=None)


def run_problems(run, jobs):
    if run is None:
        return ["no ci run for this commit from a push to main"]
    problems = []
    if not (run.get("status") == "completed" and run.get("conclusion") == "success"):
        problems.append(f"ci run {run.get('id')} is {run.get('status')}/{run.get('conclusion')}")
    by_name = {j["name"]: j for j in jobs}
    for name in REQUIRED_JOBS:
        job = by_name.get(name)
        if job is None:
            problems.append(f"job missing: {name}")
        elif job.get("conclusion") != "success":
            problems.append(f"job {name} is {job.get('status')}/{job.get('conclusion')}")
    return problems


def gh_json(path):
    out = subprocess.run(["gh", "api", "--paginate", "--slurp", path], check=True, capture_output=True, text=True).stdout
    return json.loads(out)


def tag_commit(repository, tag):
    ref = gh_json(f"repos/{repository}/git/ref/tags/{tag}")[0]["object"]
    while ref["type"] == "tag":
        ref = gh_json(f"repos/{repository}/git/tags/{ref['sha']}")[0]["object"]
    if ref["type"] != "commit" or not SHA.fullmatch(ref["sha"]):
        raise SystemExit(f"ci_passed.py: tag {tag} does not point at a commit")
    return ref["sha"]


def main(sha):
    if not SHA.fullmatch(sha):
        print(f"ci_passed.py: '{sha}' is not a full commit sha", file=sys.stderr)
        return 1
    with open(CI_WORKFLOW, encoding="utf-8") as handle:
        declared = declared_job_names(handle.read())
    undeclared = [n for n in REQUIRED_JOBS if n not in declared]
    if undeclared:
        print(f"ci_passed.py: {CI_WORKFLOW} no longer declares {undeclared}; update REQUIRED_JOBS with the branch protection", file=sys.stderr)
        return 1
    first_parent = set(subprocess.run(["git", "rev-list", "--first-parent", "origin/main"], check=True, capture_output=True,
                                      text=True).stdout.split())
    if not on_first_parent(sha, first_parent):
        print(f"ci_passed.py: {sha} is not a first-parent commit of origin/main; releases come from main itself", file=sys.stderr)
        return 1
    repository = os.environ["GITHUB_REPOSITORY"]
    pages = gh_json(f"repos/{repository}/actions/runs?head_sha={sha}&event=push&branch=main&per_page=100")
    run = ci_run([r for page in pages for r in page["workflow_runs"]], sha)
    jobs = []
    if run is not None:
        job_pages = gh_json(f"repos/{repository}/actions/runs/{run['id']}/jobs?filter=latest&per_page=100")
        jobs = [j for page in job_pages for j in page["jobs"]]
    problems = run_problems(run, jobs)
    if problems:
        print(f"ci_passed.py: {sha} is not proven by ci: " + "; ".join(problems), file=sys.stderr)
        return 1
    print(f"ci_passed.py: {sha} is on main (first parent); ci run {run['id']} succeeded with: {', '.join(REQUIRED_JOBS)}")
    return 0


def self_test():
    sha = "a" * 40
    run = {"id": 1, "head_sha": sha, "event": "push", "head_branch": "main", "path": CI_WORKFLOW, "status": "completed",
           "conclusion": "success", "run_number": 5, "run_attempt": 1}
    jobs = [{"name": n, "status": "completed", "conclusion": "success"} for n in REQUIRED_JOBS]
    assert run_problems(ci_run([run], sha), jobs) == []
    assert on_first_parent(sha, {sha}) and not on_first_parent(sha, {"b" * 40}), "a commit off the first-parent chain must be refused"
    assert ci_run([dict(run, event="pull_request")], sha) is None, "pull_request runs do not count"
    assert ci_run([dict(run, head_branch="feature")], sha) is None, "runs on other branches do not count"
    assert ci_run([dict(run, path=".github/workflows/other.yml")], sha) is None, "other workflows do not count"
    assert ci_run([dict(run, head_sha="b" * 40)], sha) is None, "runs of other commits do not count"
    rerun = dict(run, id=2, run_attempt=2, conclusion="failure")
    assert ci_run([run, rerun], sha)["id"] == 2 and run_problems(rerun, jobs), "a later failed attempt wins"
    assert run_problems(run, jobs[1:]), "a missing job fails"
    assert run_problems(run, [dict(jobs[0], conclusion="skipped")] + jobs[1:]), "skipped is not success"
    assert run_problems(None, []), "no run fails"
    with open(CI_WORKFLOW, encoding="utf-8") as handle:
        names = declared_job_names(handle.read())
    assert set(REQUIRED_JOBS) <= names, f"{CI_WORKFLOW} job names drifted: {sorted(names)}"
    print("ci_passed.py self-test passed")


if __name__ == "__main__":
    args = sys.argv[1:]
    if args == ["--self-test"]:
        self_test()
        sys.exit(0)
    if len(args) == 2 and args[0] == "--tag-commit":
        print(tag_commit(os.environ["GITHUB_REPOSITORY"], args[1]))
        sys.exit(0)
    if len(args) != 1:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    sys.exit(main(args[0]))
