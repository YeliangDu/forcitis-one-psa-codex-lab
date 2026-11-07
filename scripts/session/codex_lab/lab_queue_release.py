"""Pure release eligibility for independently collected private Lab evidence.

This module neither admits queue entries nor merges PRs. The caller must verify
repository privacy, hosted runner provenance and review independence, and use
the shared queue lock and fresh remote readback before any later write.
"""
import hashlib
import json
import re


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
WORKFLOW = ".github/workflows/lab-pr-ci.yml"
LANES = {"light", "secret", "shadow", "frontend", "backend"}


def _hold(reason):
    return dict(action="hold", reason=reason)


def _sha(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def _positive_id(value):
    """Normalize GitHub integer IDs and the workflow's environment strings."""
    if type(value) is int and value > 0:
        return str(value)
    if isinstance(value, str) and re.fullmatch(r"[1-9][0-9]*", value):
        return value
    return None


def plan_release(context, pr, run, receipt, reviews):
    """Hold on incomplete proof; return a stable identity for eligible release."""
    if not isinstance(context, dict) or not isinstance(pr, dict):
        return _hold("missing-context-or-pr")
    if (context.get("repository") != REPOSITORY or pr.get("head_repo") != REPOSITORY
            or pr.get("base_repo") != REPOSITORY):
        return _hold("wrong-repository")
    number = pr.get("number")
    if type(number) is not int or number <= 0:
        return _hold("invalid-pr-number")
    head, base = pr.get("head_sha"), pr.get("base_sha")
    if not _sha(head) or not _sha(base) or pr.get("base_ref") != "main":
        return _hold("invalid-pr-identity")
    if type(pr.get("merged")) is not bool:
        return _hold("missing-merge-readback")
    if pr["merged"]:
        if pr.get("state") != "closed":
            return _hold("inconsistent-merge-readback")
        return dict(action="skip", reason="already-merged-readback")
    if pr.get("state") != "open" or pr.get("draft") is not False:
        return _hold("closed-or-draft")
    if pr.get("held", False) is not False or pr.get("hold_reason") is not None:
        return _hold("explicit-pr-hold")
    if not _sha(context.get("main_sha")) or context["main_sha"] != base:
        return _hold("stale-base")
    if not _sha(context.get("queue_head")) or context["queue_head"] != head:
        return _hold("missing-or-stale-admission")
    required = context.get("required_reviews")
    if (not isinstance(required, list) or not required
            or any(not isinstance(kind, str) or not kind.strip() for kind in required)
            or len(set(required)) != len(required)):
        return _hold("invalid-review-requirements")
    if not isinstance(run, dict) or not isinstance(receipt, dict):
        return _hold("missing-run-or-receipt")
    if (run.get("repo") != REPOSITORY or type(run.get("pr_number")) is not int
            or run["pr_number"] != number or run.get("head_sha") != head
            or run.get("event") != "pull_request" or run.get("path") != WORKFLOW
            or run.get("runner_group") != "GitHub-hosted"):
        return _hold("foreign-or-stale-run")
    if run.get("status") != "completed" or run.get("conclusion") != "success":
        return _hold("incomplete-or-failed-run")
    run_id, attempt = _positive_id(run.get("id")), _positive_id(run.get("attempt"))
    if (run_id is None or attempt is None or _positive_id(receipt.get("run_id")) != run_id
            or _positive_id(receipt.get("run_attempt")) != attempt):
        return _hold("receipt-run-mismatch")
    if (receipt.get("head_sha") != head or receipt.get("observed_head") != head
            or receipt.get("base_sha") != base or receipt.get("ready") is not True):
        return _hold("stale-or-unready-receipt")
    lanes = receipt.get("lanes")
    if not isinstance(lanes, dict) or set(lanes) != LANES:
        return _hold("missing-or-unknown-lanes")
    for lane, evidence in lanes.items():
        if not isinstance(evidence, dict):
            return _hold("invalid-lane-evidence")
        applicability, proof = evidence.get("applicability"), evidence.get("proof")
        if lane in ("light", "secret") and applicability != "required":
            return _hold("light-and-secret-must-be-required")
        if not ((applicability == "required" and proof == "passed")
                or (applicability == "not-applicable" and proof == "not-applicable")):
            return _hold("missing-or-failed-lane-proof")
    if not isinstance(reviews, list):
        return _hold("missing-reviews")
    latest = {}
    for review in reviews:
        if (not isinstance(review, dict) or not isinstance(review.get("kind"), str)
                or not review["kind"].strip() or not _sha(review.get("head_sha"))
                or review.get("verdict") not in ("pass", "fail", "hold", "must-fix")):
            return _hold("invalid-review-evidence")
        if review["head_sha"] == head:
            latest[review["kind"]] = review["verdict"]
    if any(latest.get(kind) != "pass" for kind in required):
        return _hold("missing-or-failed-current-review")
    identity = json.dumps([REPOSITORY, number, head, base], separators=(",", ":"))
    key = hashlib.sha256(identity.encode()).hexdigest()
    return dict(action="eligible", reason="exact-head-admission-ci-and-reviews", release_key=key)
