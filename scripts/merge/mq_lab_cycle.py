"""One supervisor phase over injected ports, under one existing runner lock.

No live adapter, CLI, background loop or default process/refresh implementation
is provided. Observe must independently attest the dedicated control checkout,
current source, canonical FIFO and journal-read availability. Child output and
checkpoint data never substitute for the child's fresh release authorization.
"""
import re


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
IDENTITY = {"number", "head", "base", "target"}
POINTER = {"repository", "loaded_base", "number", "head"}
SNAPSHOT = {"repository", "loaded", "main", "control_ok", "child_stopped",
            "next_intent", "pointer", "journal", "journal_read", "health",
            "fetched_sha", "ancestry_ok", "source_ok"}


def _result(action, reason):
    return dict(action=action, reason=reason)


def _sha(value):
    return type(value) is str and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def _number(value):
    return type(value) is int and value > 0


def _intent(value):
    return (type(value) is dict and set(value) == IDENTITY and _number(value["number"])
            and _sha(value["head"]) and _sha(value["base"])
            and type(value["target"]) is str and value["target"] == "main")


def _pointer(value):
    return (type(value) is dict and set(value) == POINTER
            and type(value["repository"]) is str and value["repository"] == REPOSITORY
            and _sha(value["loaded_base"]) and _sha(value["head"]) and _number(value["number"]))


def _journal(value):
    if type(value) is not dict or type(value.get("status")) is not str:
        return False
    status = value["status"]
    if status not in ("reserved", "confirmed"):
        return False
    fields = IDENTITY | {"status", "release_key"}
    if status == "confirmed":
        fields = fields | {"merge_sha"}
    return (set(value) == fields and _intent({k: value[k] for k in IDENTITY})
            and type(value["release_key"]) is str and bool(value["release_key"].strip())
            and not any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in value["release_key"])
            and (status == "reserved" or _sha(value["merge_sha"])))


def _health_shape(value):
    if value is None:
        return True
    return (type(value) is dict and {"action", "head_sha", "latest"} <= set(value)
            and not set(value) - {"action", "head_sha", "latest", "reason"}
            and type(value["action"]) is str and value["action"] in ("healthy", "hold")
            and type(value["latest"]) is bool
            and (value["head_sha"] is None or _sha(value["head_sha"]))
            and ("reason" not in value or type(value["reason"]) is str and bool(value["reason"].strip())))


def _valid(value):
    if (type(value) is not dict or set(value) != SNAPSHOT
            or type(value["repository"]) is not str or value["repository"] != REPOSITORY
            or not _sha(value["loaded"]) or not _sha(value["main"])
            or value["control_ok"] is not True or value["source_ok"] is not True
            or value["child_stopped"] is not True or type(value["ancestry_ok"]) is not bool
            or value["fetched_sha"] is not None and not _sha(value["fetched_sha"])
            or not _health_shape(value["health"])
            or value["next_intent"] is not None and not _intent(value["next_intent"])
            or value["pointer"] is not None and not _pointer(value["pointer"])
            or type(value["journal_read"]) is not str
            or value["journal_read"] not in ("absent", "found", "unavailable")):
        return False
    if value["journal_read"] == "absent":
        return value["journal"] is None
    if value["journal_read"] == "found":
        return _journal(value["journal"])
    return False  # Unavailable is never absence or permission to use cached data.


def _healthy(snapshot, head):
    health = snapshot["health"]
    return (type(health) is dict and health["action"] == "healthy"
            and health["head_sha"] == head and health["latest"] is True)


def _bound(snapshot, pointer):
    row = snapshot["journal"]
    return (snapshot["pointer"] == pointer and snapshot["journal_read"] == "found"
            and row["number"] == pointer["number"] and row["head"] == pointer["head"]
            and row["base"] == pointer["loaded_base"] and row["target"] == "main")


def _ready_new(snapshot):
    intent = snapshot["next_intent"]
    return (snapshot["journal_read"] == "absent" and snapshot["journal"] is None
            and intent is not None and intent["base"] == snapshot["loaded"] == snapshot["main"]
            and _healthy(snapshot, snapshot["main"]))


def _ready_absent(snapshot, pointer):
    return (_ready_new(snapshot) and snapshot["pointer"] == pointer
            and snapshot["loaded"] == pointer["loaded_base"]
            and snapshot["next_intent"]["number"] == pointer["number"]
            and snapshot["next_intent"]["head"] == pointer["head"])


def _ready_confirmed(snapshot, pointer, row):
    return (_bound(snapshot, pointer) and snapshot["journal"] == row
            and row["status"] == "confirmed" and row["merge_sha"] != row["base"]
            and snapshot["main"] == row["merge_sha"] and _healthy(snapshot, row["merge_sha"])
            and snapshot["fetched_sha"] == row["merge_sha"] and snapshot["ancestry_ok"] is True)


def _with_pointer(ports, snapshot):
    pointer = dict(snapshot["pointer"])
    if snapshot["journal_read"] == "absent":
        if not _ready_absent(snapshot, pointer):
            return _result("hold", "absent-row-pointer-cannot-be-safely-retired")
        fresh = ports.observe()
        if not _valid(fresh) or not _ready_absent(fresh, pointer):
            return _result("hold", "absent-row-retirement-evidence-moved")
        if ports.retire_pointer(dict(pointer)) is not True:
            return _result("hold", "pointer-retirement-not-durable")
        return _result("idle", "absent-row-pointer-retired-no-request-this-cycle")
    if not _bound(snapshot, pointer):
        return _result("hold", "checkpoint-and-journal-identity-disagree")
    row = dict(snapshot["journal"])
    if row["status"] == "reserved":
        if snapshot["loaded"] != pointer["loaded_base"]:
            return _result("hold", "reserved-row-loaded-source-mismatch")
        fresh = ports.observe()
        if (not _valid(fresh) or not _bound(fresh, pointer) or fresh["journal"] != row
                or fresh["loaded"] != pointer["loaded_base"]):
            return _result("hold", "reservation-moved-before-readback")
        try:
            ports.recover({k: row[k] for k in IDENTITY})
        except Exception:
            pass  # No retry. Only fresh journal evidence can resolve the outcome.
        after = ports.observe()
        if (not _valid(after) or not _bound(after, pointer)
                or after["loaded"] != pointer["loaded_base"]):
            return _result("hold", "recovery-outcome-unavailable-or-inexact")
        confirmed = after["journal"]
        if (confirmed["status"] == "confirmed"
                and {k: v for k, v in confirmed.items() if k != "merge_sha"} == dict(row, status="confirmed")):
            return _result("recovered", "independent-durable-confirmation-read-no-refresh-this-cycle")
        return _result("hold", "reservation-remains-unconfirmed-no-new-request")
    if not _ready_confirmed(snapshot, pointer, row):
        return _result("hold", "confirmed-successor-health-fetch-or-ancestry-mismatch")
    if snapshot["loaded"] not in (row["base"], row["merge_sha"]):
        return _result("hold", "loaded-source-is-neither-predecessor-nor-confirmed-successor")
    fresh = ports.observe()
    if (not _valid(fresh) or not _ready_confirmed(fresh, pointer, row)
            or fresh["loaded"] != snapshot["loaded"]):
        return _result("hold", "confirmed-handoff-evidence-moved")
    if fresh["loaded"] == row["merge_sha"]:
        if ports.retire_pointer(dict(pointer)) is not True:
            return _result("hold", "successor-pointer-retirement-not-durable")
        return _result("idle", "successor-retired-old-pointer-reread-next-approval-next-cycle")
    if ports.refresh(row["base"], row["merge_sha"]) is not True:
        return _result("hold", "source-refresh-not-independently-confirmed")
    return _result("restart", "exact-source-refreshed-old-process-must-stop-before-next-item")


def run_cycle(ports):
    """Do at most one child/recovery/refresh/retirement phase under one lock.

    BaseException interruptions intentionally unwind the lock and propagate;
    the durable non-authorizing pointer survives before any child can run.
    """
    try:
        with ports.runner_lock():
            snapshot = ports.observe()
            if not _valid(snapshot):
                return _result("hold", "invalid-unavailable-or-unsafe-cycle-evidence")
            if snapshot["pointer"] is not None:
                return _with_pointer(ports, snapshot)
            if snapshot["journal_read"] != "absent":
                return _result("hold", "journal-without-original-checkpoint-no-new-request")
            if snapshot["loaded"] != snapshot["main"] or not _healthy(snapshot, snapshot["main"]):
                return _result("hold", "loaded-source-and-current-main-health-must-agree")
            if snapshot["next_intent"] is None:
                return _result("idle", "no-current-approved-fifo-item")
            if not _ready_new(snapshot):
                return _result("hold", "next-approval-does-not-match-current-loaded-base")
            intent = dict(snapshot["next_intent"])
            pointer = dict(repository=REPOSITORY, loaded_base=snapshot["loaded"],
                           number=intent["number"], head=intent["head"])
            if ports.persist_pointer(dict(pointer)) is not True:
                return _result("hold", "checkpoint-not-durably-recorded-before-child")
            fresh = ports.observe()
            if (not _valid(fresh) or not _ready_new(fresh) or fresh["pointer"] != pointer
                    or fresh["loaded"] != pointer["loaded_base"] or fresh["next_intent"] != intent):
                return _result("hold", "main-source-health-or-fifo-moved-before-child")
            try:
                ports.once(dict(fresh["next_intent"]))
            except Exception:
                pass  # Child status/output is not proof and never permits resend.
            after = ports.observe()
            if (not _valid(after) or after["pointer"] != pointer
                    or after["loaded"] != pointer["loaded_base"]):
                return _result("hold", "child-outcome-unavailable-checkpoint-preserved")
            if after["journal_read"] == "found" and _bound(after, pointer):
                return _result("attempted", "one-shot-ended-reconcile-durable-row-on-next-cycle")
            return _result("hold", "no-exact-durable-row-after-child-no-new-request")
    except Exception:
        return _result("hold", "cycle-lock-or-port-unavailable-checkpoint-not-reused-as-authority")
