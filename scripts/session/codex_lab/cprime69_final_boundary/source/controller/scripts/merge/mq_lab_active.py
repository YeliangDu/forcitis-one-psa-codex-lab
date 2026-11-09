"""Canonical queue admission and iteration over caller-bound evidence ports.

No CLI or writer is activated here. The caller verifies the physical Lab and
canonical path before constructing Queue/ReleaseJournal. Verification must use
locked helpers, never recursively acquire Queue.merge_lock.
"""
from pathlib import Path

from mq_lab import _state
from mq_lab_runtime import run_iteration


IDENTITY = ("number", "head", "base", "target")


def _hold(reason):
    return dict(action="hold", reason=reason)


def _sha(value):
    return (isinstance(value, str) and len(value) == 40
            and all(c in "0123456789abcdef" for c in value))


def _text(value):
    return isinstance(value, str) and bool(value.strip())


def _identity(value):
    return (isinstance(value, dict) and type(value.get("number")) is int
            and value["number"] > 0 and _sha(value.get("head")) and _sha(value.get("base"))
            and value.get("target") == "main")


def _proof(value, expected):
    return (_identity(value) and value.get("action") == "eligible"
            and _text(value.get("reason")) and _text(value.get("release_key"))
            and all(value.get(field) == item for field, item in expected.items()))


def _members(queue):
    path = Path(queue.path)
    if not path.is_absolute() or path.parent.resolve(strict=True) != path.parent:
        raise ValueError("Unverified physical queue path")
    present, numbers = _state(path)
    if not present:
        raise ValueError("Canonical queue has not been explicitly initialized")
    if queue.numbers() != numbers:
        raise ValueError("Queue membership changed during read")
    return numbers


def admit(queue, intent, verify):
    """Verify before stamping/appending; never overwrite conflicting admission."""
    if not _identity(intent) or set(intent) != set(IDENTITY) or not callable(verify):
        return _hold("invalid-admission-intent-or-verifier")
    intent = dict(intent)
    number, head = intent["number"], intent["head"]
    try:
        with queue.merge_lock():
            numbers = _members(queue)
            stamp = queue.head_of(number)
            if (number in numbers and stamp != head) or (stamp is not None and stamp != head):
                return _hold("conflicting-or-stale-admission-head")
            proof = verify(dict(intent))
            if not _proof(proof, intent):
                return _hold("missing-or-inexact-admission-proof")
            numbers = _members(queue)
            stamp = queue.head_of(number)
            if (number in numbers and stamp != head) or (stamp is not None and stamp != head):
                return _hold("admission-changed-during-verification")
            if number in numbers:
                return dict(action="skip", reason="exact-head-already-admitted")
            # A failed append may leave a stamp, which is not admission. Never
            # erase such evidence or infer membership from the stamp alone.
            queue.set_head(number, head)
            if queue.head_of(number) != head:
                return _hold("head-stamp-not-persisted")
            queue.enqueue(number)
            if number not in _members(queue) or queue.head_of(number) != head:
                return _hold("admission-not-persisted")
            return dict(action="admitted", reason="verified-exact-head-stamped-and-enqueued")
    except Exception:
        return _hold("canonical-admission-failed")


def run_once(queue, journal, verify, writer, *, allow_write=False, healthy=None, expected_intent=None):
    """Reuse the runtime's single lock and reservation recovery without resend."""
    def evidence(number, head):
        # run_iteration invokes this under its lock only for a new request.
        # Reserved/confirmed recovery deliberately does not seek new authority.
        expected = dict(number=number, head=head, target="main")
        proof = verify(dict(expected))
        if not _proof(proof, expected):
            return _hold("missing-or-inexact-final-proof")
        return {field: proof[field] for field in (*IDENTITY, "action", "reason", "release_key")}

    return run_iteration(queue, journal, evidence, writer, allow_write=allow_write, healthy=healthy, expected_intent=expected_intent)
