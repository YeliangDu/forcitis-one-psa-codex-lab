#!/usr/bin/env python3
"""Proves a commit M may be released: it is a first-parent commit of origin/main (a merge or direct commit on main, never a commit from
inside a merged branch), the ci workflow run by the push to main for exactly M succeeded with the three required jobs (the light run since
the heavy steps only run for queued pull requests), and the heavy steps are proven too: the pull request that merged M has a head commit H
whose pull_request ci run succeeded with the three required jobs under their full names (a light run carries a " (light)" suffix and does
not count), and M differs from H only in paths relevant_change.py calls documentation. Without such a pull request run the fallback is a
schedule or workflow_dispatch ci run of M itself with the three jobs. A commit pushed straight to main without either is refused.

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
import tempfile

REQUIRED_JOBS = (
    "backend verify (Modulith + ArchUnit guardrails + integration tests)",
    "frontend verify (typecheck + lint guardrails F1-F14 + structure + build)",
    "secret scan (gitleaks)",
)
CI_WORKFLOW = ".github/workflows/ci.yml"
SHA = re.compile(r"[0-9a-f]{40}")


def declared_job_names(workflow_text):
    """Job names as ci.yml declares them; a trailing ${{ ... }} expression (the ' (light)' suffix of pull request runs) is not part of the push run's name."""
    return {re.sub(r"\$\{\{.*?\}\}", "", name).rstrip() for name in re.findall(r"^    name: (.+)$", workflow_text, flags=re.MULTILINE)}


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


def heavy_run_problems(jobs):
    """The three required jobs under their full names, all success; a light run has other names."""
    by_name = {j["name"]: j for j in jobs}
    return [f"{name} is {by_name[name].get('conclusion') if name in by_name else 'missing'}" for name in REQUIRED_JOBS
            if name not in by_name or by_name[name].get("conclusion") != "success"]


def merging_pull_request(pulls, sha):
    """The pull request whose merge commit is sha, or None."""
    merged = [p for p in pulls if p.get("merge_commit_sha") == sha and p.get("merged_at")]
    return max(merged, key=lambda p: p.get("number", 0), default=None)


def any_relevant(paths):
    """Whether any path is something the builds read (not documentation), decided by the same script ci uses for pull requests."""
    if not paths:
        return False
    with tempfile.NamedTemporaryFile("w", suffix=".txt", encoding="utf-8") as handle:
        handle.write("\n".join(paths) + "\n")
        handle.flush()
        out = subprocess.run([sys.executable, "-I", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ci", "relevant_change.py"), "--paths-from", handle.name],
                             check=True, capture_output=True, text=True).stdout.split()
    if "relevant=true" in out:
        return True
    if "relevant=false" in out:
        return False
    raise SystemExit("ci_passed.py: relevant_change.py gave no relevant= answer")


def tree_problem(paths_differing, relevant):
    """None when M and H have the same tree (no differing paths) or differ only in documentation; relevant(paths) says whether any is code."""
    return f"the merge commit differs from the pull request head in code ({len(paths_differing)} differing path(s))" if relevant(paths_differing) else None


def full_run_problems(runs, sha, events, jobs_of):
    """A completed successful ci run of sha from one of the events, with the heavy jobs; jobs_of(run) lists its jobs."""
    for run in sorted((r for r in runs if r.get("head_sha") == sha and r.get("event") in events and r.get("path") == CI_WORKFLOW
                       and r.get("status") == "completed" and r.get("conclusion") == "success"),
                      key=lambda r: (r.get("run_number", 0), r.get("run_attempt", 0)), reverse=True):
        if not heavy_run_problems(jobs_of(run)):
            return []
    return ["no successful ci run with the full backend and frontend jobs"]


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


def jobs_of_run(repository, run):
    pages = gh_json(f"repos/{repository}/actions/runs/{run['id']}/jobs?filter=latest&per_page=100")
    return [j for page in pages for j in page["jobs"]]


def heavy_proof(repository, sha):
    """[] when the heavy steps are proven for sha, else the reasons."""
    pulls = [p for page in gh_json(f"repos/{repository}/commits/{sha}/pulls?per_page=100") for p in page]
    pull = merging_pull_request(pulls, sha)
    reasons = []
    if pull is not None:
        head = pull["head"]["sha"]
        have = subprocess.run(["git", "cat-file", "-e", f"{head}^{{commit}}"], capture_output=True).returncode == 0 or \
            subprocess.run(["git", "fetch", "-q", "origin", f"refs/pull/{pull['number']}/head"], capture_output=True).returncode == 0
        diff = subprocess.run(["git", "diff", "--name-only", head, sha], capture_output=True, text=True) if have else None
        if diff is None or diff.returncode != 0:
            reasons.append(f"pull request #{pull['number']} head {head[:12]} is not available to compare with")
        else:
            problem = tree_problem([p for p in diff.stdout.splitlines() if p], any_relevant)
            if problem:
                reasons.append(f"pull request #{pull['number']}: {problem}")
            else:
                runs = [r for page in gh_json(f"repos/{repository}/actions/runs?head_sha={head}&event=pull_request&per_page=100") for r in page["workflow_runs"]]
                reasons += full_run_problems(runs, head, ("pull_request",), lambda r: jobs_of_run(repository, r)) \
                    and [f"pull request #{pull['number']}: no successful pull_request ci run of its head with the full jobs"]
                if not reasons:
                    return []
    else:
        reasons.append("no merged pull request has this commit as its merge commit")
    runs = [r for page in gh_json(f"repos/{repository}/actions/runs?head_sha={sha}&per_page=100") for r in page["workflow_runs"]]
    fallback = full_run_problems(runs, sha, ("schedule", "workflow_dispatch"), lambda r: jobs_of_run(repository, r))
    return [] if not fallback else reasons + ["and no schedule or workflow_dispatch ci run of the commit itself with the full jobs"]


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
    heavy = heavy_proof(repository, sha)
    if heavy:
        print(f"ci_passed.py: {sha} is not proven by a full ci run: " + "; ".join(heavy), file=sys.stderr)
        return 1
    print(f"ci_passed.py: {sha} is on main (first parent); ci run {run['id']} succeeded with: {', '.join(REQUIRED_JOBS)}; the heavy steps are proven by the merging pull request or a full run")
    return 0


def self_test():
    sha = "a" * 40
    run = {"id": 1, "head_sha": sha, "event": "push", "head_branch": "main", "path": CI_WORKFLOW, "status": "completed",
           "conclusion": "success", "run_number": 5, "run_attempt": 1}
    jobs = [{"name": n, "status": "completed", "conclusion": "success"} for n in REQUIRED_JOBS]
    assert run_problems(ci_run([run], sha), jobs) == []
    assert declared_job_names("    name: a b${{ github.event_name == 'x' && ' (light)' || '' }}\n    name: c\n  name: no\n") == {"a b", "c"}, "a ${{ }} suffix is not part of the job name"
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
    full = [{"name": n, "conclusion": "success"} for n in REQUIRED_JOBS]
    light = [{"name": n + " (light)" if n != REQUIRED_JOBS[2] else n, "conclusion": "success"} for n in REQUIRED_JOBS]
    assert heavy_run_problems(full) == [] and heavy_run_problems(light), "a light run (suffixed names) does not prove the heavy steps"
    assert heavy_run_problems(full[:2]) and heavy_run_problems([dict(full[0], conclusion="failure")] + full[1:]), "a missing or failed job fails"
    pr_run = {"id": 9, "head_sha": "b" * 40, "event": "pull_request", "path": CI_WORKFLOW, "status": "completed", "conclusion": "success", "run_number": 3}
    assert full_run_problems([pr_run], "b" * 40, ("pull_request",), lambda r: full) == []
    assert full_run_problems([pr_run], "b" * 40, ("pull_request",), lambda r: light), "only the light run exists"
    assert full_run_problems([dict(pr_run, conclusion="failure")], "b" * 40, ("pull_request",), lambda r: full), "a failed run does not count"
    assert full_run_problems([pr_run], "c" * 40, ("pull_request",), lambda r: full), "a run of another commit does not count"
    assert full_run_problems([pr_run], "b" * 40, ("schedule",), lambda r: full), "a run of another event does not count"
    pulls = [{"number": 1, "merge_commit_sha": sha, "merged_at": "2026-01-01T00:00:00Z"}, {"number": 2, "merge_commit_sha": "d" * 40, "merged_at": "x"},
             {"number": 3, "merge_commit_sha": sha, "merged_at": None}]
    assert merging_pull_request(pulls, sha)["number"] == 1 and merging_pull_request(pulls, "e" * 40) is None, "only the pull request merged as sha counts"
    assert tree_problem([], any_relevant) is None, "identical trees pass"
    assert tree_problem(["README.md", "CONTRIBUTING.md"], any_relevant) is None, "a difference only in documentation passes"
    assert tree_problem(["README.md", "backend/src/main/java/A.java"], any_relevant), "a difference in code fails"
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
