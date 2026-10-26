#!/usr/bin/env python3
"""Proves a commit M may be released: it is a first-parent commit of origin/main (a merge or direct commit on main, never a commit from
inside a merged branch), the ci workflow run by the push to main for exactly M succeeded with the three required jobs (the light run: the
heavy steps only run for pull requests labelled queued), and the heavy steps are proven for the code of M:
  walking back along the first parents from M, the first commit X that is proven stops the walk; X is proven when the pull request that
  merged it ran the heavy steps (the backend "surefire and failsafe" step and the frontend "pnpm verify" step, not skipped) on its head H in a
  pull_request ci run that belongs to that pull request, whose base is main, in this repository; and X differs from H only in paths that
  relevant_change.py calls documentation. A pull request that changed only documentation (X against its first parent) adds no code, so the
  walk moves on to the first parent. A commit with no merging pull request is proven by a schedule ci run of the commit itself, with the same
  heavy steps. Anything else, a commit pushed straight to main included, is refused.

  ci_passed.py <sha>                 # needs gh authenticated (GH_TOKEN), GITHUB_REPOSITORY, and a checkout with origin/main history
  ci_passed.py --tag-commit <tag>    # prints the commit a tag points at, read through the API (works without git credentials)
  ci_passed.py --self-test

The required jobs are the job names of .github/workflows/ci.yml below; the script fails if ci.yml no longer declares them.
"""
import base64
import json
import os
import re
import shutil
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


HEAVY_STEPS = ((REQUIRED_JOBS[0], "surefire and failsafe, side by side"), (REQUIRED_JOBS[1], "Run pnpm verify"))
MAX_WALK = 30


def heavy_run_problems(jobs):
    """The three required jobs under their full names, all success, and the heavy steps of the backend and frontend jobs succeeded (not skipped)."""
    by_name = {j["name"]: j for j in jobs}
    problems = [f"{name} is {by_name[name].get('conclusion') if name in by_name else 'missing'}" for name in REQUIRED_JOBS
                if name not in by_name or by_name[name].get("conclusion") != "success"]
    for job_name, step in HEAVY_STEPS:
        found = {x.get("name"): x for x in by_name.get(job_name, {}).get("steps", [])}.get(step)
        if found is None or found.get("conclusion") != "success":
            problems.append(f"step '{step}' of {job_name} is {found.get('conclusion') if found else 'missing'}")
    return problems


def merging_pull_request(pulls, sha):
    """The pull request whose merge commit is sha and whose base is main, or None."""
    merged = [p for p in pulls if p.get("merge_commit_sha") == sha and p.get("merged_at") and p.get("base", {}).get("ref") == "main"]
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


def git_paths(old, new, cwd=None):
    """Paths that differ between two commits. Renames are not folded (a code file renamed into docs lists both paths), names are not quoted;
    a path with a line separator other than the newline counts as code through the sentinel."""
    out = subprocess.run(["git", "-c", "core.quotepath=off", "diff", "--no-renames", "--name-only", "-z", old, new], check=True, capture_output=True, cwd=cwd).stdout
    paths = [x.decode("utf-8", "replace") for x in out.split(b"\0") if x]
    return [path if path.splitlines() == [path] else "backend/" + " ".join(path.split()) for path in paths]


def pr_run_proves(runs, pull, head, repository, jobs_of):
    """Problems ([] = proven) of the newest pull_request ci run of head that belongs to the pull request and carries the full job names.
    Belonging: same repository (no fork), the pull request's head branch, and, where GitHub lists the pull requests of the run (it stops doing
    so once the head branch is deleted), that pull request with base main. Residual: a collaborator with write access could still make a
    pull request for the same head against another base whose ci.yml does nothing; that is the trust already given to anyone who may push a branch."""
    mine = [r for r in runs if r.get("head_sha") == head and r.get("event") == "pull_request" and r.get("path") == CI_WORKFLOW
            and r.get("status") == "completed" and (r.get("head_repository") or {}).get("full_name") == repository
            and r.get("head_branch") == pull["head"].get("ref")
            and (not r.get("pull_requests") or any(p.get("number") == pull["number"] and (p.get("base") or {}).get("ref") == "main" for p in r["pull_requests"]))]
    for run in sorted(mine, key=lambda r: (r.get("run_number", 0), r.get("run_attempt", 0)), reverse=True):
        jobs = jobs_of(run)
        names = {j["name"] for j in jobs}
        if all(n in names for n in REQUIRED_JOBS):
            return heavy_run_problems(jobs) if run.get("conclusion") == "success" else [f"the newest full run {run.get('id')} is {run.get('conclusion')}"]
    return ["no completed pull_request ci run of the head with the full job names"]


def schedule_run_proves(runs, sha, jobs_of):
    mine = [r for r in runs if r.get("head_sha") == sha and r.get("event") == "schedule" and r.get("path") == CI_WORKFLOW
            and r.get("status") == "completed" and r.get("conclusion") == "success"]
    for run in sorted(mine, key=lambda r: (r.get("run_number", 0), r.get("run_attempt", 0)), reverse=True):
        if not heavy_run_problems(jobs_of(run)):
            return []
    return ["no successful schedule ci run of the commit with the heavy steps"]


def prove_code(sha, api):
    """[] when the heavy steps are proven for the code of sha, else the reasons. api: pull_of(x), head_ok(pull, x), paths(old, new), first_parent(x),
    pr_run(pull), other_base(pull), schedule(x). A schedule run of the very tree of x also proves it, so it is tried before every refusal."""
    x = sha

    def refuse(reason):
        scheduled = api.schedule(x)
        return [] if not scheduled else [reason]

    for _ in range(MAX_WALK):
        pull = api.pull_of(x)
        if pull is None:
            problems = api.schedule(x)
            return [] if not problems else [f"{x[:12]}: no merging pull request, and {problems[0]}"]
        head = pull["head"]["sha"]
        if not api.head_ok(pull, x):
            return refuse(f"{x[:12]}: pull request #{pull['number']} head {head[:12]} is not available to compare with")
        others = api.other_base(pull)
        if others:
            return [f"{x[:12]}: the head of pull request #{pull['number']} also heads pull request(s) {others} against another base; refusing (a run could belong to them)"]
        if any_relevant(api.paths(head, x)):
            return refuse(f"{x[:12]}: differs from the head of pull request #{pull['number']} in code")
        parent = api.first_parent(x)
        added_code = parent is None or any_relevant(api.paths(parent, x))
        run_problems_ = api.pr_run(pull)
        if not run_problems_:
            return []
        if added_code:
            return refuse(f"{x[:12]}: pull request #{pull['number']} changed code and {run_problems_[0]}")
        x = parent
    return [f"no proven commit within {MAX_WALK} first parents back from {sha[:12]}"]


GH_TIMEOUT = 120   # seconds


def gh_json(path):
    try:
        out = subprocess.run(["gh", "api", "--paginate", "--slurp", path], check=True, capture_output=True, text=True, timeout=GH_TIMEOUT).stdout
    except subprocess.TimeoutExpired:
        raise SystemExit(f"ci_passed.py: gh api {path} timed out after {GH_TIMEOUT}s")
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


class GitHubApi:
    def __init__(self, repository):
        self.repository = repository

    def pull_of(self, x):
        return merging_pull_request([p for page in gh_json(f"repos/{self.repository}/commits/{x}/pulls?per_page=100") for p in page], x)

    def head_ok(self, pull, x):
        head = pull["head"]["sha"]
        if not SHA.fullmatch(head):
            return False
        if subprocess.run(["git", "cat-file", "-e", f"{head}^{{commit}}"], capture_output=True).returncode == 0:
            return True
        token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
        if not token:
            return False
        header = "AUTHORIZATION: basic " + base64.b64encode(f"x-access-token:{token}".encode()).decode()
        # the header travels in the environment, not in argv where ps could show it
        env = dict(os.environ, GIT_TERMINAL_PROMPT="0", GIT_CONFIG_COUNT="1", GIT_CONFIG_KEY_0="http.extraheader", GIT_CONFIG_VALUE_0=header)
        try:
            return subprocess.run(["git", "fetch", "-q", "origin", f"refs/pull/{pull['number']}/head"], capture_output=True, timeout=120, env=env).returncode == 0
        except subprocess.TimeoutExpired:
            return False

    def paths(self, old, new):
        return git_paths(old, new)

    def other_base(self, pull):
        pulls = [p for page in gh_json(f"repos/{self.repository}/commits/{pull['head']['sha']}/pulls?per_page=100") for p in page]
        return sorted(p["number"] for p in pulls if (p.get("base") or {}).get("ref") != "main" and p.get("number") != pull["number"])

    def first_parent(self, x):
        out = subprocess.run(["git", "rev-parse", "--verify", "-q", f"{x}^1"], capture_output=True, text=True)
        return out.stdout.strip() if out.returncode == 0 and SHA.fullmatch(out.stdout.strip()) else None

    def pr_run(self, pull):
        head = pull["head"]["sha"]
        runs = [r for page in gh_json(f"repos/{self.repository}/actions/runs?head_sha={head}&event=pull_request&per_page=100") for r in page["workflow_runs"]]
        return pr_run_proves(runs, pull, head, self.repository, lambda r: jobs_of_run(self.repository, r))

    def schedule(self, x):
        runs = [r for page in gh_json(f"repos/{self.repository}/actions/runs?head_sha={x}&event=schedule&per_page=100") for r in page["workflow_runs"]]
        return schedule_run_proves(runs, x, lambda r: jobs_of_run(self.repository, r))


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
    heavy = prove_code(sha, GitHubApi(repository))
    if heavy:
        print(f"ci_passed.py: {sha} is not proven by a full ci run: " + "; ".join(heavy), file=sys.stderr)
        return 1
    print(f"ci_passed.py: {sha} is on main (first parent); ci run {run['id']} succeeded with: {', '.join(REQUIRED_JOBS)}; the heavy steps are proven by the merging pull request or a full run")
    return 0


def fake_hung_gh():
    """A directory whose `gh` sleeps, to put first on PATH so the timeout path can be exercised."""
    tmp = tempfile.mkdtemp()
    path = os.path.join(tmp, "gh")
    with open(path, "w") as handle:
        handle.write("#!/bin/sh\nexec sleep 5\n")
    os.chmod(path, 0o755)
    return tmp


def gh_timeout_self_test(call, error):
    global GH_TIMEOUT
    saved_timeout, saved_path = GH_TIMEOUT, os.environ["PATH"]
    GH_TIMEOUT, fake_dir = 1, fake_hung_gh()
    os.environ["PATH"] = fake_dir + os.pathsep + saved_path
    try:
        call()
    except error as exc:
        assert "timed out" in str(exc), exc
    else:
        raise AssertionError("a hung gh call did not time out")
    finally:
        GH_TIMEOUT, os.environ["PATH"] = saved_timeout, saved_path
        shutil.rmtree(fake_dir, ignore_errors=True)


def self_test():
    gh_timeout_self_test(lambda: gh_json('repos/x/y'), SystemExit)
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
    def job(name, steps=()):
        return {"name": name, "conclusion": "success", "steps": [{"name": n, "conclusion": c} for n, c in steps]}
    ran = [(REQUIRED_JOBS[0], [("surefire and failsafe, side by side", "success")]), (REQUIRED_JOBS[1], [("Run pnpm verify", "success")]), (REQUIRED_JOBS[2], [])]
    full = [job(n, st) for n, st in ran]
    skipped = [job(REQUIRED_JOBS[0], [(HEAVY_STEPS[0][1], "skipped")]), job(REQUIRED_JOBS[1], [(HEAVY_STEPS[1][1], "skipped")]), job(REQUIRED_JOBS[2])]
    light = [dict(job(n, st), name=n + " (light)") for n, st in ran[:2]] + [job(REQUIRED_JOBS[2])]
    assert heavy_run_problems(full) == []
    assert heavy_run_problems(skipped), "full job names with skipped heavy steps do not prove anything"
    assert heavy_run_problems(light), "a light run (suffixed names) does not prove the heavy steps"
    assert heavy_run_problems(full[:2]) and heavy_run_problems([dict(full[0], conclusion="failure")] + full[1:]), "a missing or failed job fails"
    pull = {"number": 7, "head": {"sha": "b" * 40, "ref": "feature"}}
    run = {"id": 9, "head_sha": "b" * 40, "event": "pull_request", "path": CI_WORKFLOW, "status": "completed", "conclusion": "success", "run_number": 3,
           "head_repository": {"full_name": "o/r"}, "head_branch": "feature", "pull_requests": [{"number": 7, "base": {"ref": "main"}}]}
    ok = lambda r: full  # noqa: E731
    assert pr_run_proves([run], pull, "b" * 40, "o/r", ok) == []
    assert pr_run_proves([run], pull, "b" * 40, "o/r", lambda r: light), "only a light run exists"
    assert pr_run_proves([dict(run, conclusion="failure")], pull, "b" * 40, "o/r", ok), "a failed newest full run fails"
    assert pr_run_proves([run, dict(run, id=10, run_number=4, conclusion="failure")], pull, "b" * 40, "o/r", ok), "a later failed run wins over an earlier success"
    assert pr_run_proves([run], pull, "c" * 40, "o/r", ok), "a run of another commit does not count"
    assert pr_run_proves([dict(run, event="push")], pull, "b" * 40, "o/r", ok), "another event does not count"
    assert pr_run_proves([dict(run, head_repository={"full_name": "x/fork"})], pull, "b" * 40, "o/r", ok), "a fork run does not count"
    assert pr_run_proves([dict(run, pull_requests=[{"number": 8, "base": {"ref": "main"}}])], pull, "b" * 40, "o/r", ok), "a run of another pull request does not count"
    assert pr_run_proves([dict(run, pull_requests=[{"number": 7, "base": {"ref": "x"}}])], pull, "b" * 40, "o/r", ok), "a run for a pull request with another base does not count"
    assert pr_run_proves([dict(run, pull_requests=[])], pull, "b" * 40, "o/r", ok) == [], "GitHub empties the pull requests of a run once the head branch is gone"
    assert pr_run_proves([dict(run, head_branch="other", pull_requests=[])], pull, "b" * 40, "o/r", ok), "a run of another branch does not count"
    pulls = [{"number": 1, "merge_commit_sha": sha, "merged_at": "2026-01-01T00:00:00Z", "base": {"ref": "main"}}, {"number": 2, "merge_commit_sha": "d" * 40, "merged_at": "x", "base": {"ref": "main"}},
             {"number": 3, "merge_commit_sha": sha, "merged_at": None, "base": {"ref": "main"}}, {"number": 4, "merge_commit_sha": sha, "merged_at": "x", "base": {"ref": "other"}}]
    assert merging_pull_request(pulls, sha)["number"] == 1 and merging_pull_request(pulls, "e" * 40) is None, "only the pull request merged into main as sha counts"
    assert not any_relevant([]) and not any_relevant(["README.md", "CONTRIBUTING.md"]) and any_relevant(["README.md", "backend/src/main/java/A.java"])
    with tempfile.TemporaryDirectory() as repo:
        def git(*args):
            return subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", *args], cwd=repo, check=True, capture_output=True, text=True).stdout
        git("init", "-q")
        os.makedirs(os.path.join(repo, "backend"))
        with open(os.path.join(repo, "backend", "Sec.java"), "w") as handle:
            handle.write("class Sec { /* a body long enough that git sees the rename */ }\n" * 20)
        git("add", "-A"), git("commit", "-q", "-m", "a")
        first = git("rev-parse", "HEAD").strip()
        os.makedirs(os.path.join(repo, "docs"))
        git("mv", "backend/Sec.java", "docs/Sec.md"), git("commit", "-q", "-m", "b")
        renamed = git_paths(first, git("rev-parse", "HEAD").strip(), cwd=repo)
        assert sorted(renamed) == ["backend/Sec.java", "docs/Sec.md"] and any_relevant(renamed), "a code file renamed into docs is a code difference"
    class Fake:
        """pulls: {commit: (pull, first_parent)}; code_paths: {(old, new): [paths]}; proven: set of pull numbers with heavy runs; scheduled: set of commits"""
        def __init__(self, pulls, diffs, proven, scheduled=()):
            self.pulls, self.diffs, self.proven, self.scheduled = pulls, diffs, set(proven), set(scheduled)
        def pull_of(self, x):
            return self.pulls.get(x, (None, None))[0]
        def head_ok(self, pull, x):
            return True
        def paths(self, old, new):
            return self.diffs.get((old, new), [])
        def first_parent(self, x):
            return self.pulls.get(x, (None, None))[1]
        def pr_run(self, pull):
            return [] if pull["number"] in self.proven else ["no run"]
        other = ()
        def other_base(self, pull):
            return list(self.other)
        def schedule(self, x):
            return [] if x in self.scheduled else ["no schedule run"]
    def pr(n, head):
        return {"number": n, "head": {"sha": head, "ref": "b"}}
    m1, m2, m3 = "1" * 40, "2" * 40, "3" * 40
    h1, h2, h3 = "a1" * 20, "a2" * 20, "a3" * 20
    code, docs = ["backend/A.java"], ["README.md"]
    chain = {m3: (pr(3, h3), m2), m2: (pr(2, h2), m1), m1: (pr(1, h1), None)}
    assert prove_code(m1, Fake(chain, {(h1, m1): [], (None, m1): code}, {1})) == [], "identical trees with a heavy run pass"
    assert prove_code(m1, Fake(chain, {(h1, m1): docs, (None, m1): code}, {1})) == [], "a difference only in documentation passes"
    assert prove_code(m1, Fake(chain, {(h1, m1): code, (None, m1): code}, {1})), "a difference in code from the tested head fails"
    assert prove_code(m1, Fake(chain, {(h1, m1): [], (None, m1): code}, set())), "a pull request that changed code and has no heavy run fails"
    # a documentation-only pull request on top of code that was never proven does not launder it
    assert prove_code(m3, Fake(chain, {(h3, m3): code, (m2, m3): docs, (h2, m2): code, (m1, m2): code}, {3})), "a docs pull request with a heavy run over a concurrent code merge fails: the tested head lacks that code"
    assert prove_code(m3, Fake(chain, {(h3, m3): [], (m2, m3): docs, (h2, m2): code, (m1, m2): code}, set())), "a docs pull request (heavy steps skipped) over an unproven code merge fails"
    # ... but on top of a proven one it passes by walking back
    assert prove_code(m3, Fake(chain, {(h3, m3): [], (m2, m3): docs, (h2, m2): [], (m1, m2): code}, {2})) == [], "a docs pull request over a proven code merge passes"
    other = Fake(chain, {(h1, m1): [], (None, m1): code}, {1})
    other.other = (99,)
    assert prove_code(m1, other), "a head that also heads a pull request against another base is refused"
    assert prove_code(m1, Fake(chain, {(h1, m1): code, (None, m1): code}, {1}, {m1})) == [], "a schedule run of the commit itself rescues a pull request that cannot be matched"
    assert prove_code("4" * 40, Fake({}, {}, set())), "a commit without a pull request and without a schedule run is refused"
    assert prove_code("4" * 40, Fake({}, {}, set(), {"4" * 40})) == [], "a schedule run proves a commit without a pull request"
    with open(CI_WORKFLOW, encoding="utf-8") as handle:
        names_in_ci = handle.read()
    assert HEAVY_STEPS[0][1] in names_in_ci and "run: pnpm verify" in names_in_ci, "the heavy step names drifted from ci.yml"
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
