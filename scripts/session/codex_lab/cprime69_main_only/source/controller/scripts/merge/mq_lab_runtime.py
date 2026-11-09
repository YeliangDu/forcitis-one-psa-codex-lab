"""One locked FIFO iteration over injected canonical queue/release ports.

This is not a main writer binding. The evidence port must independently verify
health, admission, CI and reviews; the writer must enforce expected base/head.
Journal persistence and canonical-path verification belong to their adapters.
"""
import re


IDENTITY = ("number", "head", "base", "target")


def _hold(reason):
    return dict(action="hold", reason=reason)


def _sha(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def _text(value):
    return isinstance(value, str) and bool(value.strip())


def _matches(value, intent):
    return (isinstance(value, dict) and type(value.get("number")) is int
            and all(value.get(field) == intent[field] for field in IDENTITY))


def _valid_record(record, number, head):
    if (not isinstance(record, dict) or type(record.get("number")) is not int
            or record["number"] != number or record.get("head") != head
            or not _sha(record.get("base")) or record.get("target") != "main"
            or not _text(record.get("release_key"))):
        return False
    fields = set(IDENTITY) | {"release_key", "status"}
    if record.get("status") == "reserved":
        return set(record) == fields
    return (record.get("status") == "confirmed" and set(record) == fields | {"merge_sha"}
            and _sha(record.get("merge_sha")))


def _current(queue, number, head):
    numbers = queue.numbers()
    return (isinstance(numbers, list) and bool(numbers)
            and type(numbers[0]) is int and numbers[0] == number
            and queue.head_of(number) == head)


def _clear(queue, record, action):
    number, head = record["number"], record["head"]
    if not _current(queue, number, head):
        return _hold("confirmed-item-no-longer-matches-admission")
    queue.remove(number)
    drop_exact = getattr(queue, "drop_head_exact", None)
    if callable(drop_exact):
        drop_exact(number, head)
    elif queue.head_of(number) == head:
        queue.drop_head(number)
    else:
        return _hold("confirmed-stamp-moved-after-fifo-removal")
    return dict(action=action, reason="confirmed-exact-item-cleared", merge_sha=record["merge_sha"])


def exact_release_health(healthy, merge):
    if not callable(healthy):
        return False
    try:
        proof = healthy(merge)
        return (isinstance(proof, dict) and proof.get("action") == "healthy"
                and proof.get("head_sha") == merge and proof.get("expired") is not True)
    except Exception:
        return False


def _recover(queue, journal, writer, record, healthy):
    intent = {field: record[field] for field in IDENTITY}
    proof = writer.readback(dict(intent))
    if (not _matches(proof, intent) or proof.get("merged") is not True
            or proof.get("target_contains_merge") is not True or not _sha(proof.get("merge_sha"))):
        return _hold("reserved-outcome-unconfirmed-or-inexact")
    merge = proof["merge_sha"]
    if record["status"] == "confirmed" and record["merge_sha"] != merge:
        return _hold("confirmed-merge-readback-moved")
    if not exact_release_health(healthy, merge):
        return _hold("exact-new-main-health-required")
    if not _current(queue, record["number"], record["head"]):
        return _hold("admission-changed-before-confirmation")
    if record["status"] == "confirmed":
        return _clear(queue, record, "skip")
    confirmed = dict(record, status="confirmed", merge_sha=merge)
    if journal.confirm(dict(confirmed)) is not True:
        return _hold("confirmation-not-durable")
    return _clear(queue, confirmed, "confirmed")


def run_iteration(queue, journal, evidence, writer, *, allow_write=False, healthy=None, expected_intent=None):
    """Process only the first entry, reserving before at most one request.

    All port calls occur under one merge_lock acquisition. Existing uncertain
    work is read back, never resent, including when new writes are disabled.
    No admission, retry, substitute queue or legacy fallback is created here.
    """
    try:
        with queue.merge_lock():
            numbers = queue.numbers()
            if not isinstance(numbers, list):
                return _hold("malformed-canonical-queue")
            if not numbers:
                return dict(action="idle", reason="canonical-queue-empty")
            number = numbers[0]
            if type(number) is not int or number <= 0:
                return _hold("invalid-first-queue-item")
            head = queue.head_of(number)
            if not _sha(head):
                return _hold("missing-or-invalid-admission-head")
            if expected_intent is not None and (number != expected_intent.get("number") or head != expected_intent.get("head")):
                return _hold("first-card-admission-scope-mismatch")
            record = journal.load(number)
            if record is not None:
                if not _valid_record(record, number, head):
                    return _hold("malformed-or-stale-journal-record")
                record = dict(record)
                if expected_intent is not None and not _matches(record, expected_intent):
                    return _hold("first-card-recovery-scope-mismatch")
                return _recover(queue, journal, writer, record, healthy)
            if allow_write is not True:
                return _hold("new-release-writes-disabled")
            decision = evidence(number, head)
            if (not isinstance(decision, dict) or decision.get("action") != "eligible"
                    or not _text(decision.get("reason")) or type(decision.get("number")) is not int
                    or decision["number"] != number or decision.get("head") != head
                    or not _sha(decision.get("base")) or decision.get("target") != "main"
                    or not _text(decision.get("release_key"))):
                return _hold("missing-or-inexact-locked-evidence")
            if expected_intent is not None and not _matches(decision, expected_intent):
                return _hold("first-card-proof-scope-mismatch")
            if not _current(queue, number, head):
                return _hold("admission-changed-during-evidence-read")
            record = {field: decision[field] for field in IDENTITY}
            record.update(status="reserved", release_key=decision["release_key"])
            if journal.reserve(dict(record)) is not True:
                return _hold("reservation-not-new-or-not-durable")
            if not _current(queue, number, head):
                return _hold("admission-changed-after-reservation")
            intent = {field: record[field] for field in IDENTITY}
            try:
                writer.send(dict(intent))
            except Exception:
                # Unknown response or transport failure is reconciled using
                # independent proof. The durable reservation is never cleared.
                pass
            return _recover(queue, journal, writer, record, healthy)
    except Exception:
        return _hold("locked-queue-iteration-failed")
