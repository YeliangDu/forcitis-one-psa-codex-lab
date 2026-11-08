"""Pure first-slice worker handoff plans, never execution or write authority.

Snapshots must be independently collected. A later adapter must own the runner
lock, use a dedicated controlled checkout, and revalidate before every step.
This module has no process, filesystem, Git, network or queue operations.
"""
import re


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
ORIGIN = "git@github.com:" + REPOSITORY + ".git"
SNAPSHOT_FIELDS = {"repository", "loaded_commit", "observed_main", "observed_pr_head",
                   "journal", "health", "control", "process"}
CONTROL_FIELDS = {"clean", "remotes", "fetch_urls", "push_urls", "fetched_sha",
                  "fast_forward", "merge_ancestor"}


def _hold(reason):
    return dict(action="hold", reason=reason, steps=[], allows_new_write=False)


def _sha(value):
    return type(value) is str and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def _strings(value, expected):
    return type(value) is list and all(type(item) is str for item in value) and value == expected


def _reservation(journal, loaded):
    if type(journal) is not dict:
        return False
    status = journal.get("status")
    if type(status) is not str or status not in ("reserved", "confirmed"):
        return False
    fields = {"number", "status", "head", "base", "target", "release_key"}
    if status == "confirmed":
        fields.add("merge_sha")
    if (set(journal) != fields or type(journal.get("number")) is not int or journal["number"] <= 0
            or not _sha(journal.get("head")) or not _sha(journal.get("base"))
            or journal["base"] != loaded or type(journal.get("target")) is not str
            or journal["target"] != "main" or type(journal.get("release_key")) is not str
            or not journal["release_key"].strip()
            or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in journal["release_key"])):
        return False
    return status == "reserved" or _sha(journal.get("merge_sha"))


def plan_handoff(snapshot):
    """Return hold, original-reservation readback, or a pinned refresh plan.

    Recovery needs no successor health/fetch proof because it only observes the
    original uncertain write. It never refreshes source or requests new work.
    A confirmed handoff requires the complete exact successor evidence below.
    """
    if type(snapshot) is not dict or set(snapshot) != SNAPSHOT_FIELDS:
        return _hold("invalid-handoff-snapshot-shape")
    if type(snapshot["repository"]) is not str or snapshot["repository"] != REPOSITORY:
        return _hold("wrong-personal-lab-repository")
    loaded = snapshot["loaded_commit"]
    if not _sha(loaded):
        return _hold("invalid-loaded-commit")
    if any(snapshot[field] is not None and not _sha(snapshot[field])
           for field in ("observed_main", "observed_pr_head")):
        return _hold("malformed-observed-commit")
    health = snapshot["health"]
    if health is not None:
        if (type(health) is not dict or not {"action", "head_sha", "latest"} <= set(health)
                or set(health) - {"action", "head_sha", "latest", "reason"}
                or type(health["action"]) is not str or health["action"] not in ("healthy", "hold")
                or type(health["latest"]) is not bool
                or health["head_sha"] is not None and not _sha(health["head_sha"])
                or "reason" in health and (type(health["reason"]) is not str or not health["reason"].strip())):
            return _hold("malformed-health-evidence")
    control = snapshot["control"]
    if type(control) is not dict or set(control) != CONTROL_FIELDS:
        return _hold("invalid-control-evidence")
    if (control["clean"] is not True or not _strings(control["remotes"], ["origin"])
            or not _strings(control["fetch_urls"], [ORIGIN])
            or not _strings(control["push_urls"], [ORIGIN])):
        return _hold("dirty-or-untrusted-control-checkout")
    if (type(control["fast_forward"]) is not bool or type(control["merge_ancestor"]) is not bool
            or control["fetched_sha"] is not None and not _sha(control["fetched_sha"])):
        return _hold("malformed-fetch-or-ancestry-evidence")
    process = snapshot["process"]
    if (type(process) is not dict or set(process) != {"old_exited", "runner_lock_held", "worker_count"}
            or process["old_exited"] is not True or process["runner_lock_held"] is not True
            or type(process["worker_count"]) is not int or process["worker_count"] != 0):
        return _hold("old-worker-or-runner-lock-not-safe")
    journal = snapshot["journal"]
    if not _reservation(journal, loaded):
        return _hold("missing-malformed-or-unbound-journal")
    if journal["status"] == "reserved":
        return dict(action="recover-readonly", reason="uncertain-reservation-needs-original-readback",
                    reservation=dict(journal), steps=["readback"], allows_new_write=False)
    successor = journal["merge_sha"]
    if (not _sha(snapshot["observed_main"]) or snapshot["observed_main"] != successor
            or successor == loaded or not _sha(snapshot["observed_pr_head"])
            or snapshot["observed_pr_head"] != journal["head"]):
        return _hold("confirmed-merge-main-or-reviewed-head-mismatch")
    if (type(health) is not dict or not {"action", "head_sha", "latest"} <= set(health)
            or type(health.get("action")) is not str or health["action"] != "healthy"
            or not _sha(health.get("head_sha")) or health["head_sha"] != successor
            or health["latest"] is not True):
        return _hold("missing-failed-or-stale-successor-health")
    if (control["fetched_sha"] != successor or control["fast_forward"] is not True
            or control["merge_ancestor"] is not True):
        return _hold("fetched-successor-or-ancestry-unverified")
    return dict(action="refresh", reason="exact-confirmed-successor-ready-for-controlled-handoff",
                old_commit=loaded, new_commit=successor,
                fetch_argv=["fetch", "--no-tags", "origin", successor],
                steps=["fetch", "verify-fetched", "verify-ancestry", "checkout", "start-worker"],
                allows_new_write=False)
