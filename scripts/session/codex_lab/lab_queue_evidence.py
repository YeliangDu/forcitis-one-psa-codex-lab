"""Normalize supplied GitHub evidence without fetching, writing or releasing."""
import importlib.util
from datetime import datetime
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[3]


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# post_review uses a sibling import. Limit that search path to module loading;
# all paths and policy come from this checkout, never the caller's current CWD.
_previous_path = sys.path[:]
try:
    sys.path.insert(0, str(ROOT / "scripts/merge"))
    _review = _load("lab_queue_review_parser", ROOT / "scripts/merge/post_review.py")
finally:
    sys.path[:] = _previous_path
_release = _load("lab_queue_release_planner", Path(__file__).with_name("lab_queue_release.py"))
try:
    _FIELDS = tuple(_review.load_policy(ROOT / "policy.json")[0]["header_fields"])
except _review.Refused:
    _FIELDS = None


def _require(condition, reason):
    if not condition:
        raise ValueError(reason)


def _positive(value):
    return type(value) is int and value > 0


def _repository(value, repo_id, full=False):
    _require(isinstance(value, dict) and _positive(value.get("id"))
             and value["id"] == repo_id, "foreign-repository-id")
    if full or "full_name" in value:
        _require(value.get("full_name") == _release.REPOSITORY, "foreign-repository-name")


def _select_run(runs, pr, repo_id):
    """Select newest current-PR/head run, then verify its exact association.

    Readers use this same selection before fetching jobs/artifacts. A newer
    pending/red/stale-base run cannot cause fallback to older successful proof.
    """
    _require(isinstance(runs, list), "missing-runs")
    number, head = pr["number"], pr["head"]["sha"]
    candidates = {}
    for run in runs:
        _require(isinstance(run, dict), "malformed-run")
        if run.get("event") != "pull_request" or run.get("path") != _release.WORKFLOW or run.get("head_sha") != head:
            continue
        _repository(run.get("repository"), repo_id)
        _require(_positive(run.get("id")) and _positive(run.get("run_attempt"))
                 and isinstance(run.get("pull_requests"), list), "malformed-run-identity")
        created = run.get("created_at")
        _require(isinstance(created, str) and bool(created), "missing-run-timestamp")
        _require(datetime.fromisoformat(created.replace("Z", "+00:00")).tzinfo is not None,
                 "invalid-run-timestamp")
        associations = run["pull_requests"]
        _require(all(isinstance(a, dict) and _positive(a.get("number")) for a in associations),
                 "malformed-run-association")
        matching = [a for a in associations if a["number"] == number]
        if not matching:
            continue
        _require(len(matching) == 1, "ambiguous-pr-association")
        key = (run["id"], run["run_attempt"])
        _require(key not in candidates, "duplicate-run-identity")
        candidates[key] = (run, matching[0])
    _require(bool(candidates), "missing-current-head-run")
    key = max(candidates)
    run, association = candidates[key]
    for branch in ("head", "base"):
        value = association.get(branch)
        _require(isinstance(value, dict) and value.get("sha") == pr[branch]["sha"], "stale-run-association")
        _repository(value.get("repo"), repo_id)
        _require(value.get("ref") == pr[branch]["ref"], "wrong-run-association-ref")
    return key, run


def _reviews(rows, trusted, head):
    required = trusted.get("required_reviews")
    assigned = trusted.get("reviewers")
    _require(isinstance(required, list) and bool(required)
             and all(isinstance(k, str) and k in ("首轮", "终审") for k in required)
             and len(set(required)) == len(required), "invalid-required-review-kinds")
    _require(isinstance(assigned, dict) and set(assigned) == set(required), "invalid-review-assignments")
    suffixes = set()
    for kind, reviewer in assigned.items():
        _require(_review.parse_reviewer(reviewer) == kind, "invalid-assigned-reviewer")
        suffix = reviewer.split("-", 2)[2]
        _require(suffix not in suffixes, "shared-review-session")
        suffixes.add(suffix)
    _require(isinstance(rows, list) and _FIELDS is not None, "missing-reviews-or-policy")
    normalized = []
    for row in rows:
        _require(isinstance(row, dict), "malformed-review")
        header = _review.parse_header(row.get("body"), _FIELDS)
        # Ordinary GitHub comments/reviews are not structured release proof.
        if header is None:
            continue
        kind = header["kind"]
        if assigned.get(kind) != header["reviewer"]:
            continue
        if row.get("commit_id") != head or header["commit"] != head:
            continue
        state = row.get("state")
        _require(state in ("APPROVED", "COMMENTED", "CHANGES_REQUESTED", "DISMISSED", "PENDING"),
                 "unknown-review-state")
        verdict = ("pass" if state in ("APPROVED", "COMMENTED")
                   and header["verdict"] == _review.PASS else "fail")
        normalized.append(dict(kind=kind, verdict=verdict, head_sha=head))
    return required, normalized


def plan_from_github(repository, main, queue, pr, runs, jobs, artifacts, reviews, trusted):
    """Decide from caller-fetched snapshots, not from labels or green summaries.

    The caller attests workflow integrity and independent reviewer assignments.
    It must collect complete pages, extract only this run's artifact, and obtain
    the existing queue lock and fresh remote readback before a later action.
    """
    try:
        _require(isinstance(repository, dict) and repository.get("full_name") == _release.REPOSITORY
                 and repository.get("private") is True and _positive(repository.get("id")),
                 "not-private-lab")
        repo_id = repository["id"]
        _require(isinstance(trusted, dict) and trusted.get("workflow_verified") is True
                 and trusted.get("held") is False, "missing-trusted-entry-proof-or-held")
        _require(isinstance(main, dict) and main.get("ref") == "refs/heads/main"
                 and isinstance(main.get("object"), dict)
                 and _release._sha(main["object"].get("sha")), "invalid-main")
        _require(isinstance(pr, dict) and _positive(pr.get("number")), "invalid-pr")
        number = pr["number"]
        for branch in ("head", "base"):
            _require(isinstance(pr.get(branch), dict) and _release._sha(pr[branch].get("sha"))
                     and isinstance(pr[branch].get("ref"), str) and bool(pr[branch]["ref"]), "invalid-pr-branch")
            _repository(pr[branch].get("repo"), repo_id, full=True)
        head, base = pr["head"]["sha"], pr["base"]["sha"]
        _require(pr["base"]["ref"] == "main", "wrong-base-ref")
        normalized_pr = dict(number=number, state=pr.get("state"), draft=pr.get("draft"),
                             merged=pr.get("merged"), head_sha=head, base_sha=base, base_ref="main",
                             head_repo=_release.REPOSITORY, base_repo=_release.REPOSITORY)
        # Remote merged readback makes a retry a skip without fresh CI/reviews.
        if pr.get("merged") is True:
            return _release.plan_release(dict(repository=_release.REPOSITORY), normalized_pr, None, None, [])
        _require(isinstance(queue, dict) and queue.get("present") is True
                 and isinstance(queue.get("numbers"), list)
                 and all(_positive(n) for n in queue["numbers"])
                 and len(set(queue["numbers"])) == len(queue["numbers"])
                 and number in queue["numbers"] and isinstance(queue.get("heads"), dict),
                 "missing-canonical-admission")
        _require(all(_positive(n) and _release._sha(sha) for n, sha in queue["heads"].items()),
                 "malformed-queue-heads")
        _require(queue["heads"].get(number) == head, "stale-or-missing-admission-head")
        _require(isinstance(runs, list) and isinstance(jobs, dict) and isinstance(artifacts, dict),
                 "missing-action-evidence")
        key, run = _select_run(runs, pr, repo_id)
        _require(isinstance(jobs.get(key), list)
                 and all(isinstance(job, dict) for job in jobs[key]), "missing-jobs")
        matching_jobs = [job for job in jobs[key] if job.get("name") == "Lab exact-head verification"]
        _require(len(matching_jobs) == 1, "missing-or-ambiguous-verification-job")
        job = matching_jobs[0]
        _require(job.get("conclusion") == "success" and job.get("head_sha") == head
                 and job.get("runner_group_name") == "GitHub Actions"
                 and job.get("labels") == ["ubuntu-24.04"], "unverified-hosted-job")
        _require(isinstance(artifacts.get(key), list)
                 and all(isinstance(a, dict) for a in artifacts[key]), "missing-artifacts")
        name = f"lab-pr-{number}-{head}-{key[1]}"
        matching_artifacts = [a for a in artifacts[key] if a.get("name") == name]
        _require(len(matching_artifacts) == 1 and matching_artifacts[0].get("expired") is False
                 and isinstance(matching_artifacts[0].get("receipt"), dict), "missing-or-ambiguous-receipt")
        required, normalized_reviews = _reviews(reviews, trusted, head)
        context = dict(repository=_release.REPOSITORY, main_sha=main["object"]["sha"],
                       queue_head=queue["heads"][number], required_reviews=required)
        normalized_run = dict(id=key[0], attempt=key[1], event=run.get("event"), path=run.get("path"),
                              repo=_release.REPOSITORY, pr_number=number, head_sha=run.get("head_sha"),
                              status=run.get("status"), conclusion=run.get("conclusion"), runner_group="GitHub-hosted")
        return _release.plan_release(context, normalized_pr, normalized_run,
                                     matching_artifacts[0]["receipt"], normalized_reviews)
    except ValueError as error:
        return dict(action="hold", reason=str(error) or "invalid-github-evidence")
    except (TypeError, KeyError, AttributeError):
        return dict(action="hold", reason="missing-malformed-or-inexact-github-evidence")
