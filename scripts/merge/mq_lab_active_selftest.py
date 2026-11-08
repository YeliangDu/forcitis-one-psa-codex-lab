"""Canonical Queue/SQLite binding acceptance; synthetic proof and writer only."""
import copy
from contextlib import contextmanager
import importlib.util
from pathlib import Path
import tempfile
import types
import unittest

from mq_queue import Queue
from mq_lab_journal import ReleaseJournal

HEAD, BASE, MERGE, OTHER = (c * 40 for c in "abcd")


def active():
    path = Path(__file__).with_name("mq_lab_active.py")
    if not path.exists():
        return types.SimpleNamespace(admit=lambda *a: dict(action="hold", reason="unbound"),
                                     run_once=lambda *a, **k: dict(action="hold", reason="unbound"))
    spec = importlib.util.spec_from_file_location("mq_active_acceptance", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class CheckedQueue(Queue):
    def __init__(self, path, case):
        super().__init__(path)
        self.case = case
        self.held = False
        self.entries = 0

    @contextmanager
    def merge_lock(self):
        self.case.assertFalse(self.held, "no nested merge flock")
        with super().merge_lock():
            self.held = True
            self.entries += 1
            try: yield
            finally: self.held = False


class ActiveQueueAcceptanceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.queue_path = self.root / "merge.queue"
        self.queue_path.write_text("")
        self.journal_path = self.root / "release.sqlite"
        self.queue = CheckedQueue(str(self.queue_path), self)
        self.journal = ReleaseJournal(self.journal_path)
        self.intent = dict(number=14, head=HEAD, base=BASE, target="main")
        self.verifications = []
        self.proof_override = None
        self.remote_proof = None
        self.sends = self.readbacks = 0
        self.timeout = False

    def verify(self, intent):
        self.assertTrue(self.queue.held, "proof read under actual queue lock")
        self.verifications.append(copy.deepcopy(intent))
        if self.proof_override is not None:
            return copy.deepcopy(self.proof_override)
        return dict(self.intent, action="eligible", reason="synthetic-current-proof", release_key="synthetic-stable-key")

    def send(self, intent):
        self.assertTrue(self.queue.held)
        self.assertEqual(self.intent, intent)
        persisted = ReleaseJournal(self.journal_path).load(14)
        self.assertIsNotNone(persisted, "real SQLite reservation precedes request")
        self.assertEqual("reserved", persisted["status"])
        self.sends += 1
        if self.timeout: raise TimeoutError("synthetic unknown effect")
        return dict(success=True)

    def readback(self, intent):
        self.assertTrue(self.queue.held)
        self.assertEqual(self.intent, intent)
        self.readbacks += 1
        return copy.deepcopy(self.remote_proof)

    def state(self):
        fresh = Queue(str(self.queue_path))
        return fresh.numbers(), fresh.head_of(14)

    def admit(self):
        result = active().admit(self.queue, copy.deepcopy(self.intent), self.verify)
        self.assertFalse(self.queue.held)
        self.assertTrue(result["reason"])
        return result

    def once(self, enabled=None):
        args = (self.queue, self.journal, self.verify, self)
        result = active().run_once(*args) if enabled is None else active().run_once(*args, allow_write=enabled)
        self.assertFalse(self.queue.held)
        self.assertTrue(result["reason"])
        return result

    def test_exact_admission_idempotent_and_conflicting_head_not_restamped(self):
        self.assertEqual("admitted", self.admit()["action"])
        self.assertEqual(([14], HEAD), self.state())
        self.assertEqual("skip", self.admit()["action"])
        self.assertEqual(([14], HEAD), self.state())
        self.intent["head"] = OTHER
        self.assertEqual("hold", self.admit()["action"])
        self.assertEqual(([14], HEAD), self.state())

    def test_missing_or_inexact_admission_does_not_write_stamp_or_membership(self):
        for field, value in (("action", "hold"), ("head", OTHER), ("base", OTHER),
                             ("number", 27), ("target", "topic"), ("release_key", "")):
            with self.subTest(field=field):
                self.proof_override = dict(self.intent, action="eligible", reason="synthetic", release_key="synthetic-key")
                self.proof_override[field] = value
                self.assertEqual("hold", self.admit()["action"])
                self.assertEqual(([], None), self.state())
        self.assertGreater(self.queue.entries, 0, "admission checks require shared lock")

    def test_default_disabled_and_final_reverification_hold(self):
        self.assertEqual("admitted", self.admit()["action"])
        self.assertEqual("hold", self.once()["action"])
        self.assertEqual(0, self.sends)
        self.assertIsNone(self.journal.load(14))
        self.proof_override = dict(self.intent, action="hold", reason="review-moved", release_key="synthetic-key")
        self.assertEqual("hold", self.once(True)["action"])
        self.assertEqual(([14], HEAD), self.state())
        self.assertEqual(0, self.sends)
        self.assertGreaterEqual(len(self.verifications), 2)

    def test_complete_real_file_path_rechecks_then_confirms_before_cleanup(self):
        self.assertEqual("admitted", self.admit()["action"])
        self.remote_proof = dict(self.intent, merged=True, target_contains_merge=True, merge_sha=MERGE)
        entries = self.queue.entries
        result = self.once(True)
        self.assertEqual("confirmed", result["action"])
        self.assertEqual(MERGE, result["merge_sha"])
        self.assertEqual(entries + 1, self.queue.entries, "run_once reuses one runtime lock")
        self.assertGreaterEqual(len(self.verifications), 2, "stored admission is not current approval")
        self.assertEqual(1, self.sends)
        self.assertEqual(([], None), self.state())
        record = ReleaseJournal(self.journal_path).load(14)
        self.assertIsNotNone(record)
        self.assertEqual("confirmed", record["status"])
        self.assertEqual(MERGE, record["merge_sha"])

    def test_restart_unknown_effect_only_reads_and_exact_confirmation_cleans(self):
        self.assertEqual("admitted", self.admit()["action"])
        self.timeout = True
        self.assertEqual("hold", self.once(True)["action"])
        record = ReleaseJournal(self.journal_path).load(14)
        self.assertIsNotNone(record)
        self.assertEqual("reserved", record["status"])
        self.assertEqual(([14], HEAD), self.state())
        self.assertEqual(1, self.sends)
        self.queue = CheckedQueue(str(self.queue_path), self)
        self.journal = ReleaseJournal(self.journal_path)
        def no_new_authorization(intent):
            self.fail("reservation recovery must not request new permission")
        self.verify = no_new_authorization
        self.remote_proof = dict(self.intent, merged=True, target_contains_merge=True, merge_sha=MERGE)
        self.remote_proof["head"] = OTHER
        self.assertEqual("hold", self.once()["action"])
        self.assertEqual(([14], HEAD), self.state())
        self.assertEqual(record, ReleaseJournal(self.journal_path).load(14))
        self.remote_proof["head"] = HEAD
        self.assertEqual("confirmed", self.once()["action"])
        self.assertEqual(1, self.sends)
        self.assertEqual(([], None), self.state())
        self.assertEqual("confirmed", ReleaseJournal(self.journal_path).load(14)["status"])


if __name__ == "__main__":
    unittest.main()
