"""Consume only the exact current main's hosted push health evidence."""
import re


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
REPOSITORY_ID = 1449415666
WORKFLOW = ".github/workflows/lab-pr-ci.yml"


def _require(condition, reason):
    if not condition:
        raise ValueError(reason)


def _sha(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def _positive(value):
    return type(value) is int and value > 0


def _id(value):
    if _positive(value):
        return str(value)
    if isinstance(value, str) and re.fullmatch(r"[1-9][0-9]*", value):
        return value
    return None


def _head(value):
    _require(isinstance(value, dict) and value.get("ref") == "refs/heads/main"
             and isinstance(value.get("object"), dict) and _sha(value["object"].get("sha")),
             "invalid-main-ref")
    return value["object"]["sha"]


def _public_repository(value):
    """Full native repository objects; never a visibility-only allow switch."""
    return (isinstance(value, dict) and value.get("full_name") == REPOSITORY
            and type(value.get("id")) is int and value["id"] == REPOSITORY_ID
            and value.get("private") is False
            and ("visibility" not in value or value["visibility"] == "public")
            and ("fork" not in value or value["fork"] is False))


def check_main_health(github):
    """Read-only health, independent of PR receipts and queue admission.

    Missing bootstrap, failed/pending latest attempts and all read errors hold.
    No prior green run or legacy ci.yml signal can substitute for this proof.
    """
    try:
        repo = github.repository()
        _require(_public_repository(repo), "not-fixed-public-lab")
        head = _head(github.main_ref())
        runs = github.main_runs(head)
        _require(isinstance(runs, list), "missing-main-runs")
        candidates = {}
        for run in runs:
            _require(isinstance(run, dict), "malformed-main-run")
            if (run.get("event") != "push" or run.get("head_branch") != "main"
                    or run.get("path") != WORKFLOW or run.get("head_sha") != head):
                continue
            origin = run.get("repository")
            _require(_public_repository(origin) and origin["id"] == repo["id"], "foreign-main-run")
            _require(_positive(run.get("id")) and _positive(run.get("run_attempt")), "invalid-main-run-identity")
            key = (run["id"], run["run_attempt"])
            _require(key not in candidates, "ambiguous-main-run")
            candidates[key] = run
        _require(bool(candidates), "main-health-not-available")
        key = max(candidates)
        run = candidates[key]
        _require(run.get("status") == "completed" and run.get("conclusion") == "success",
                 "latest-main-run-not-successful")
        jobs = github.jobs(*key)
        _require(isinstance(jobs, list) and all(isinstance(j, dict) for j in jobs), "missing-main-jobs")
        matching = [j for j in jobs if j.get("name") == "Lab main health"]
        _require(len(matching) == 1, "missing-or-ambiguous-main-job")
        job = matching[0]
        _require(job.get("conclusion") == "success" and job.get("head_sha") == head
                 and job.get("runner_group_name") == "GitHub Actions"
                 and job.get("labels") == ["ubuntu-24.04"], "unverified-main-hosted-job")
        artifacts = github.main_artifacts(*key, head)
        _require(isinstance(artifacts, list) and all(isinstance(a, dict) for a in artifacts), "missing-main-artifacts")
        name = f"lab-main-health-{head}-{key[1]}"
        matching = [a for a in artifacts if a.get("name") == name]
        _require(len(matching) == 1 and matching[0].get("expired") is False, "missing-or-ambiguous-main-artifact")
        health = matching[0].get("health")
        _require(isinstance(health, dict) and health.get("kind") == "main-health"
                 and health.get("event") == "push" and health.get("head_sha") == head
                 and health.get("observed_head") == head and health.get("healthy") is True
                 and _id(health.get("run_id")) == str(key[0])
                 and _id(health.get("run_attempt")) == str(key[1]), "inexact-main-health")
        checks = health.get("checks")
        _require(isinstance(checks, dict) and set(checks) == {"pin", "light", "secret"}
                 and all(value == "success" for value in checks.values()), "missing-or-failed-main-checks")
        _require(_head(github.main_ref()) == head, "main-moved-during-health-read")
        return dict(action="healthy", reason="exact-current-main-hosted-health", head_sha=head)
    except ValueError as error:
        return dict(action="hold", reason=str(error) or "invalid-main-health")
    except Exception:
        return dict(action="hold", reason="main-health-read-failed")
