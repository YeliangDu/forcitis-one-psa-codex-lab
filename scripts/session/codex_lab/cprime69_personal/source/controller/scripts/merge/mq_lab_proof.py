"""Read-only current release proof for a separately trusted local approval.

The caller owns the canonical queue lock and actual admission. The candidate
stamp supplied to the pure planner represents this approval's proposed identity;
it does not establish on-disk membership or enable a writer. Source trust and
the bootstrap baseline attestation are independent caller responsibilities.
"""
import importlib.util
from pathlib import Path


DIRECTORY = Path(__file__).resolve().parents[1] / "session" / "codex_lab"
INITIAL_MAIN = "a8454ced28ab18fce37d495f21d0daae2fa6ce8d"
IDENTITY = ("number", "head", "base", "target")


def _load(name):
    spec = importlib.util.spec_from_file_location("mq_proof_" + name, DIRECTORY / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


_evidence = _load("lab_queue_evidence")
_health = _load("lab_main_health")
_release = _evidence._release
_require = _evidence._require


def _main(main):
    _require(isinstance(main, dict) and main.get("ref") == "refs/heads/main"
             and isinstance(main.get("object"), dict) and _release._sha(main["object"].get("sha")),
             "invalid-current-main")
    return main["object"]["sha"]


def _pr(pr, intent, repo_id):
    _require(isinstance(pr, dict) and type(pr.get("number")) is int
             and pr["number"] == intent["number"] and pr.get("state") == "open"
             and pr.get("draft") is False and pr.get("merged") is False,
             "closed-draft-or-wrong-pr")
    _require(pr.get("held", False) is False and pr.get("hold_reason") is None, "explicit-pr-hold")
    for branch in ("head", "base"):
        value = pr.get(branch)
        _require(isinstance(value, dict) and value.get("sha") == intent[branch]
                 and isinstance(value.get("ref"), str) and bool(value["ref"]), "stale-pr-identity")
        _evidence._repository(value.get("repo"), repo_id, full=True)
    _require(pr["base"]["ref"] == "main", "wrong-pr-base-ref")
    _require(isinstance(pr.get("title"), str) and isinstance(pr.get("body"), str), "missing-pr-title-or-card")
    _require(_evidence._review.issue_gate.title_problem(pr["title"], pr["body"]) is None,
             "invalid-pr-title-or-card-link")


class _HealthReader:
    """Observe genuine absence separately from a red, malformed or stale run."""
    def __init__(self, github, base):
        self.github, self.base = github, base
        self.missing = False

    def __getattr__(self, name):
        return getattr(self.github, name)

    def main_runs(self, head):
        rows = self.github.main_runs(head)
        self.missing = head == self.base and isinstance(rows, list) and len(rows) == 0
        return rows


def verify_intent(intent, approval, github, trusted_source, bootstrap_check):
    """Combine source authority, exact hosted proof, reviews and fresh main.

    Normal approval always needs current main health. Bootstrap only accepts
    the separately attested PR 15/initial-main pair and an actually empty main
    run listing; it cannot turn a red or unreadable health signal into approval.
    """
    try:
        _require(isinstance(intent, dict) and set(intent) in (
            set(IDENTITY), {"number", "head", "target"}), "invalid-intent-shape")
        _require(isinstance(approval, dict) and _evidence._positive(intent.get("number"))
                 and _release._sha(intent.get("head")) and intent.get("target") == "main",
                 "invalid-release-intent")
        _require(type(approval.get("number")) is int and approval["number"] == intent["number"]
                 and approval.get("head") == intent["head"] and approval.get("target") == "main"
                 and _release._sha(approval.get("base")) and type(approval.get("repo_id")) is int
                 and approval["repo_id"] == _evidence.REPOSITORY_ID,
                 "missing-or-inexact-local-approval")
        if "base" in intent:
            _require(intent["base"] == approval["base"], "unapproved-base")
        prepared = dict(number=intent["number"], head=intent["head"], base=approval["base"], target="main")
        mode = approval.get("mode")
        # The retired PR15/private baseline is historical only. Keep the public
        # entry incapable of invoking its bootstrap callback or health fallback.
        _require(mode in ("normal", "bootstrap"), "unknown-approval-mode")
        _require(mode == "normal", "historical-bootstrap-unavailable-in-public-proof")
        _require(callable(trusted_source) and trusted_source(dict(prepared)) is True, "untrusted-gate-source")
        repository = github.repository()
        _evidence._repository(repository, _evidence.REPOSITORY_ID, full=True)
        _require(repository["id"] == approval["repo_id"], "wrong-public-lab-identity")
        main = github.main_ref()
        _require(_main(main) == prepared["base"], "approved-base-is-not-current-main")
        pr = github.pull(prepared["number"])
        _pr(pr, prepared, repository["id"])
        health_reader = _HealthReader(github, prepared["base"])
        health = _health.check_main_health(health_reader)
        if mode == "normal":
            _require(health.get("action") == "healthy" and health.get("head_sha") == prepared["base"],
                     "current-main-health-required")
        else:
            _require(prepared["number"] == 15 and prepared["base"] == INITIAL_MAIN,
                     "bootstrap-only-approved-pr15-initial-main")
            _require(health.get("action") == "hold" and health.get("reason") == "main-health-not-available"
                     and health_reader.missing, "bootstrap-cannot-bypass-existing-or-unreadable-health")
            _require(callable(bootstrap_check) and bootstrap_check(dict(prepared), dict(approval)) is True,
                     "missing-independent-bootstrap-attestation")
        runs = github.runs(prepared["head"])
        key, run = _evidence._select_run(runs, pr, repository["id"])
        _require(_release._positive_id(approval.get("run_id")) == str(key[0])
                 and _release._positive_id(approval.get("run_attempt")) == str(key[1]),
                 "latest-run-does-not-match-approved-attempt")
        _require(run.get("status") == "completed" and run.get("conclusion") == "success",
                 "latest-approved-run-not-successful")
        jobs = {key: github.jobs(*key)}
        artifacts = {key: github.artifacts(*key)}
        reviews = github.reviews(prepared["number"])
        trusted = dict(workflow_verified=True, held=False,
                       required_reviews=approval.get("required_reviews"), reviewers=approval.get("reviewers"))
        candidate = dict(present=True, numbers=[prepared["number"]],
                         heads={prepared["number"]: prepared["head"]})
        decision = _evidence.plan_from_github(repository, main, candidate, pr,
                                              runs, jobs, artifacts, reviews, trusted)
        _require(decision.get("action") == "eligible", decision.get("reason", "incomplete-pr-proof"))
        current_pr = github.pull(prepared["number"])
        _pr(current_pr, prepared, repository["id"])
        current_main = github.main_ref()
        _require(_main(current_main) == prepared["base"], "main-moved-during-release-proof")
        _require(trusted_source(dict(prepared)) is True, "gate-source-trust-changed")
        decision = _evidence.plan_from_github(repository, current_main, candidate, current_pr,
                                              runs, jobs, artifacts, reviews, trusted)
        _require(decision.get("action") == "eligible", decision.get("reason", "final-proof-held"))
        return dict(prepared, action="eligible", reason="current-exact-approved-release-proof",
                    release_key=decision["release_key"])
    except ValueError as error:
        return dict(action="hold", reason=str(error) or "invalid-live-release-proof")
    except Exception:
        return dict(action="hold", reason="live-release-proof-unavailable")
