"""Observe one admitted item under the caller-bound canonical queue lock.

No Queue constructor, admission, merge, dispatch or write API is used here.
The caller must supply a verified canonical queue and a read-only GitHub port
returning complete evidence. An eligible result is not a remote reservation.
"""
import importlib.util
from pathlib import Path


_spec = importlib.util.spec_from_file_location(
    "lab_queue_observe_evidence", Path(__file__).with_name("lab_queue_evidence.py"))
_evidence = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_evidence)
_release = _evidence._release


def _hold(reason):
    return dict(action="hold", reason=reason)


def _validate_pr(pr, number, repo_id):
    _evidence._require(isinstance(pr, dict) and type(pr.get("number")) is int
                       and pr["number"] == number, "invalid-pr-readback")
    for branch in ("head", "base"):
        value = pr.get(branch)
        _evidence._require(isinstance(value, dict) and _release._sha(value.get("sha"))
                           and isinstance(value.get("ref"), str) and bool(value["ref"]),
                           "invalid-pr-branch")
        _evidence._repository(value.get("repo"), repo_id, full=True)
    _evidence._require(pr["base"]["ref"] == "main", "wrong-base-ref")


def _main_sha(main):
    _evidence._require(isinstance(main, dict) and main.get("ref") == "refs/heads/main"
                       and isinstance(main.get("object"), dict)
                       and _release._sha(main["object"].get("sha")), "invalid-main-readback")
    return main["object"]["sha"]


def observe_release(number, queue, github, trusted):
    """Read only after canonical admission, and recheck before eligibility."""
    if queue is None or type(number) is not int or number <= 0:
        return _hold("missing-canonical-queue-or-invalid-item")
    if (not isinstance(trusted, dict) or trusted.get("workflow_verified") is not True
            or trusted.get("held") is not False):
        return _hold("missing-trusted-entry-proof-or-held")
    try:
        with queue.merge_lock():
            numbers = queue.numbers()
            if (not isinstance(numbers, list) or not all(_evidence._positive(n) for n in numbers)
                    or len(set(numbers)) != len(numbers) or number not in numbers):
                return _hold("missing-or-invalid-canonical-membership")
            head = queue.head_of(number)
            if not _release._sha(head):
                return _hold("missing-or-invalid-canonical-head")
            admission = dict(present=True, numbers=numbers, heads={number: head})
            repository = github.repository()
            if (not isinstance(repository, dict) or repository.get("full_name") != _release.REPOSITORY
                    or repository.get("private") is not True or not _evidence._positive(repository.get("id"))):
                return _hold("not-private-lab")
            repo_id = repository["id"]
            main = github.main_ref()
            base = _main_sha(main)
            pr = github.pull(number)
            _validate_pr(pr, number, repo_id)
            if pr["head"]["sha"] != head:
                return _hold("stale-admission-head")
            if pr.get("merged") is True:
                return _evidence.plan_from_github(repository, main, admission, pr,
                                                  [], {}, {}, [], trusted)
            if (pr.get("state") != "open" or pr.get("draft") is not False
                    or pr.get("merged") is not False or pr["base"]["sha"] != base):
                return _hold("closed-draft-or-stale-pr")
            runs = github.runs(head)
            key, run = _evidence._select_run(runs, pr, repo_id)
            if run.get("status") != "completed" or run.get("conclusion") != "success":
                return _hold("latest-run-incomplete-or-failed")
            jobs = {key: github.jobs(*key)}
            artifacts = {key: github.artifacts(*key)}
            reviews = github.reviews(number)
            decision = _evidence.plan_from_github(repository, main, admission, pr,
                                                  runs, jobs, artifacts, reviews, trusted)
            if decision.get("action") != "eligible":
                return decision
            # Lock remains held through fresh PR/main reads and final planning.
            current_pr = github.pull(number)
            _validate_pr(current_pr, number, repo_id)
            current_main = github.main_ref()
            if (current_pr["head"]["sha"] != head or current_pr["base"]["sha"] != base
                    or _main_sha(current_main) != base):
                return _hold("head-or-main-moved-during-observation")
            return _evidence.plan_from_github(repository, current_main, admission, current_pr,
                                              runs, jobs, artifacts, reviews, trusted)
    except Exception:
        # Includes transport/lock/context-manager failures. Never retry, acquire
        # a substitute queue or turn an unavailable read into release proof.
        return _hold("observation-failed-or-incomplete")
