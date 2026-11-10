"""Disposable release state machine over injected ports only.

No production adapter is selected here. A writer must atomically compare the
expected target base and incorporate exactly the approved head. The journal
must provide durable reservations under the supplied shared lock. These are
port requirements, not claims about GitHub's ordinary PR merge endpoint.
"""
import re


IDENTITY = ("number", "head", "base", "target")


def _hold(reason):
    return dict(action="hold", reason=reason)


def _sha(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def _key(value):
    return isinstance(value, str) and bool(value.strip())


def _intent(value):
    return (isinstance(value, dict) and set(value) == set(IDENTITY)
            and type(value.get("number")) is int and value["number"] > 0
            and _sha(value.get("head")) and _sha(value.get("base"))
            and isinstance(value.get("target"), str)
            and re.fullmatch(r"lab/disposable-[a-z0-9-]+", value["target"]) is not None)


def _matches(value, intent):
    return (isinstance(value, dict) and type(value.get("number")) is int
            and all(value.get(field) == intent[field] for field in IDENTITY))


def _valid_record(record, intent):
    if not _matches(record, intent) or not _key(record.get("release_key")):
        return False
    fields = set(IDENTITY) | {"status", "release_key"}
    if record.get("status") == "reserved":
        return set(record) == fields
    if record.get("status") == "confirmed":
        return set(record) == fields | {"merge_sha"} and _sha(record.get("merge_sha"))
    return False


def _confirm(intent, record, journal, writer):
    proof = writer.readback(dict(intent))
    if (not _matches(proof, intent) or proof.get("merged") is not True
            or proof.get("target_contains_merge") is not True or not _sha(proof.get("merge_sha"))):
        return _hold("unconfirmed-or-inexact-readback")
    confirmed = dict(record, status="confirmed", merge_sha=proof["merge_sha"])
    if journal.confirm(dict(confirmed)) is False:
        return _hold("confirmation-not-durable")
    return dict(action="confirmed", reason="independent-exact-readback-confirmed",
                release_key=record["release_key"], merge_sha=proof["merge_sha"])


def attempt_release(intent, lock, journal, writer, observe_locked):
    """Reserve before one disposable request; recover uncertainty without retry.

    Validation rejects main and malformed intent before any port interaction.
    Confirmed records skip; reserved records only read back and may confirm.
    Nothing clears or overwrites an uncertain reservation to permit a resend.
    """
    if not _intent(intent):
        return _hold("invalid-or-nondisposable-intent")
    intent = dict(intent)
    try:
        with lock():
            record = journal.load(dict(intent))
            if record is not None:
                if not _valid_record(record, intent):
                    return _hold("malformed-or-conflicting-journal-record")
                record = dict(record)
                if record["status"] == "confirmed":
                    return dict(action="skip", reason="already-durably-confirmed",
                                release_key=record["release_key"], merge_sha=record["merge_sha"])
                # Recovery verifies the past effect, not permission for a new
                # request. Even revoked eligibility cannot justify a resend.
                return _confirm(intent, record, journal, writer)
            evidence = observe_locked(dict(intent))
            if (not _matches(evidence, intent) or evidence.get("action") != "eligible"
                    or not _key(evidence.get("release_key"))):
                return _hold("missing-or-inexact-locked-eligibility")
            record = dict(intent, status="reserved", release_key=evidence["release_key"])
            if journal.reserve(dict(record)) is not True:
                return _hold("reservation-not-new-or-not-durable")
            try:
                # The response is deliberately ignored: timeout and success
                # both require independent readback, never a second request.
                writer.send(dict(intent))
            except Exception:
                pass
            return _confirm(intent, record, journal, writer)
    except Exception:
        return _hold("locked-release-io-failed")
