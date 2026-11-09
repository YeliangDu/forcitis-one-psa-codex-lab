"""Disposable write-state acceptance using fake ports only; hosted execution."""
import copy
from contextlib import contextmanager
import importlib.util
from pathlib import Path
import unittest

HEAD, BASE, MERGE = "b" * 40, "a" * 40, "c" * 40


def attempt():
    source = Path(__file__).with_name("lab_queue_attempt.py")
    if not source.exists():
        return lambda *args: dict(action="hold", reason="manual-writer-off")
    spec = importlib.util.spec_from_file_location("lab_attempt_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.attempt_release


class Ports:
    def __init__(self, case):
        self.case = case
        self.held = False
        self.events = []
        self.record = None
        self.proof = None
        self.send_error = False
        self.reject = False
        self.fail = None
        self.effect = True
        self.reserve_ok = True

    def io(self, name):
        self.case.assertTrue(self.held, "all port I/O must hold the shared lock")
        self.events.append(name)
        if self.fail == name:
            raise OSError("synthetic I/O failure")

    @contextmanager
    def lock(self):
        if self.fail == "lock":
            raise OSError("synthetic lock failure")
        self.case.assertFalse(self.held)
        self.held = True
        try:
            yield
        finally:
            self.held = False

    def observe(self, intent):
        self.io("observe")
        return dict(intent, action="eligible", release_key="synthetic-stable-key")

    def load(self, intent):
        self.io("load")
        return copy.deepcopy(self.record)

    def reserve(self, record):
        self.io("reserve")
        if not self.reserve_ok:
            return False
        self.case.assertIsNone(self.record)
        self.case.assertEqual("reserved", record["status"])
        self.record = copy.deepcopy(record)
        return True

    def confirm(self, record):
        self.io("confirm")
        self.case.assertIsNotNone(self.record)
        self.case.assertIn("readback", self.events)
        self.case.assertEqual("confirmed", record["status"])
        self.case.assertEqual(MERGE, record["merge_sha"])
        self.record = copy.deepcopy(record)

    def send(self, intent):
        self.io("send")
        self.case.assertIsNotNone(self.record, "durable reserve must precede request")
        self.case.assertEqual("reserved", self.record["status"])
        if self.reject:
            raise ValueError("synthetic atomic base/head comparison rejected")
        if self.effect:
            self.proof = dict(intent, merged=True, merge_sha=MERGE, target_contains_merge=True)
        if self.send_error:
            raise TimeoutError("synthetic timeout after possible remote effect")
        return {"success": True}  # Deliberately insufficient confirmation evidence.

    def readback(self, intent):
        self.io("readback")
        return copy.deepcopy(self.proof)


class LabQueueAttemptAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.intent = dict(number=14, head=HEAD, base=BASE, target="lab/disposable-synthetic-14")
        self.ports = Ports(self)

    def run_attempt(self, observe=None):
        result = attempt()(copy.deepcopy(self.intent), self.ports.lock, self.ports,
                           self.ports, observe or self.ports.observe)
        self.assertFalse(self.ports.held, "lock released after every outcome")
        self.assertTrue(result["reason"])
        return result

    def assert_confirmed(self):
        self.assertIsNotNone(self.ports.record)
        self.assertEqual("confirmed", self.ports.record["status"])
        self.assertEqual(MERGE, self.ports.record["merge_sha"])
        for field, value in self.intent.items():
            self.assertEqual(value, self.ports.record[field])

    def test_disposable_only_and_malformed_intent_rejected_without_io(self):
        for field, value in (("target", "main"), ("target", "lab/disposable-../main"),
                             ("target", "lab/disposable-Upper"), ("number", True),
                             ("number", 0), ("head", "bad"), ("base", None)):
            with self.subTest(field=field, value=value):
                self.intent[field] = value
                self.assertEqual("hold", self.run_attempt()["action"])
                self.assertEqual([], self.ports.events)
                self.setUp()

    def test_happy_reserve_precedes_send_and_independent_readback_confirms(self):
        self.run_attempt()
        self.assert_confirmed()
        events = self.ports.events
        self.assertLess(events.index("observe"), events.index("reserve"))
        self.assertLess(events.index("reserve"), events.index("send"))
        self.assertLess(events.index("send"), events.index("readback"))
        self.assertLess(events.index("readback"), events.index("confirm"))
        self.assertEqual(1, events.count("send"))

    def test_missing_stale_or_mismatched_locked_eligibility_holds(self):
        for field, value in (("action", "hold"), ("release_key", ""), ("number", 15),
                             ("head", MERGE), ("base", MERGE), ("target", "lab/disposable-other")):
            with self.subTest(field=field):
                self.ports = Ports(self)
                def observe(intent):
                    evidence = self.ports.observe(intent)
                    evidence[field] = value
                    return evidence
                self.assertEqual("hold", self.run_attempt(observe)["action"])
                self.assertNotIn("send", self.ports.events)
                self.assertNotIn("reserve", self.ports.events)
        self.ports = Ports(self)
        self.assertEqual("hold", self.run_attempt(lambda intent: None)["action"])
        self.assertNotIn("send", self.ports.events)

    def test_timeout_after_effect_confirms_from_readback(self):
        self.ports.send_error = True
        self.run_attempt()
        self.assert_confirmed()
        self.assertEqual(1, self.ports.events.count("send"))
        self.assertLess(self.ports.events.index("send"), self.ports.events.index("readback"))

    def test_unknown_outcome_and_restart_never_send_twice(self):
        self.ports.effect = False
        self.ports.send_error = True
        self.assertEqual("hold", self.run_attempt()["action"])
        self.assertIsNotNone(self.ports.record)
        self.assertEqual("reserved", self.ports.record["status"])
        saved = copy.deepcopy(self.ports.record)
        restarted = Ports(self)
        restarted.record = copy.deepcopy(saved)
        self.ports = restarted
        self.assertEqual("hold", self.run_attempt()["action"])
        self.assertIn("readback", self.ports.events)
        self.assertNotIn("send", self.ports.events)
        self.assertEqual(saved, self.ports.record)
        self.ports.proof = dict(self.intent, merged=True, merge_sha=MERGE, target_contains_merge=True)
        self.run_attempt()
        self.assert_confirmed()
        self.assertNotIn("send", self.ports.events)

    def test_confirmed_record_skips_without_remote_write(self):
        self.ports.record = dict(self.intent, status="confirmed", release_key="synthetic-stable-key", merge_sha=MERGE)
        self.assertEqual("skip", self.run_attempt()["action"])
        self.assertNotIn("send", self.ports.events)
        self.assertNotIn("reserve", self.ports.events)
        self.assertNotIn("confirm", self.ports.events)

    def test_inexact_readback_or_success_response_never_confirms(self):
        for field, value in (("head", MERGE), ("base", MERGE), ("target", "main"),
                             ("number", 15), ("merged", False), ("target_contains_merge", False),
                             ("merge_sha", "bad"), ("merge_sha", None)):
            with self.subTest(field=field, value=value):
                self.ports = Ports(self)
                self.ports.effect = False
                self.ports.proof = dict(self.intent, merged=True, merge_sha=MERGE, target_contains_merge=True)
                self.ports.proof[field] = value
                self.assertEqual("hold", self.run_attempt()["action"])
                self.assertIn("readback", self.ports.events)
                self.assertNotIn("confirm", self.ports.events)
        self.ports = Ports(self)
        self.ports.effect = False
        self.assertEqual("hold", self.run_attempt()["action"])
        self.assertNotIn("confirm", self.ports.events)

    def test_atomic_writer_rejection_and_lock_io_failures_hold(self):
        self.ports.reject = True
        self.assertEqual("hold", self.run_attempt()["action"])
        self.assertIn("readback", self.ports.events)
        self.assertNotIn("confirm", self.ports.events)
        for failure in ("lock", "observe", "load", "reserve", "readback", "confirm"):
            with self.subTest(failure=failure):
                self.ports = Ports(self)
                self.ports.fail = failure
                self.assertEqual("hold", self.run_attempt()["action"])
                self.assertFalse(self.ports.held)
                if failure in ("lock", "observe", "load", "reserve"):
                    self.assertNotIn("send", self.ports.events)
        self.ports = Ports(self)
        self.ports.reserve_ok = False
        self.assertEqual("hold", self.run_attempt()["action"])
        self.assertNotIn("send", self.ports.events)


if __name__ == "__main__":
    unittest.main()
