"""Issue #21 recover-only operator seam; intended destination scripts/merge/.

Source reviewed: 283b01a3392bf8c9a12a099efe1d355e50fdec44.
Hosted execution only: real temporary Queue/ReleaseJournal, fake remote writer.
This does not prove the two real disposable-ref releases required by #21.
"""
import copy
import fcntl
import importlib.util
from pathlib import Path
import tempfile
import types
import unittest

from mq_queue import Queue
from mq_lab_journal import ReleaseJournal

HEAD, BASE, MERGE, OTHER = (c * 40 for c in "abcd")


def operator():
    spec = importlib.util.spec_from_file_location(
        "recover_only_acceptance", Path(__file__).with_name("mq_lab_operator.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.run_command


class RecoveryOnlyAcceptanceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.common = self.root / "forcitis-one-psa-codex-lab" / ".git"
        self.directory = self.common / "merge-queue"
        self.directory.mkdir(parents=True)
        self.path = self.directory / "merge.queue"
        self.path.write_text("")
        self.queue = Queue(str(self.path))
        self.queue.set_head(15, HEAD)
        self.queue.enqueue(15)
        self.db = self.directory / "releases.sqlite3"
        self.record = dict(status="reserved", number=15, head=HEAD, base=BASE,
                           target="main", release_key="synthetic-prior-reservation")
        self.intent = {key: self.record[key] for key in ("number", "head", "base", "target")}
        self.remote = None
        self.readbacks = []
        self.forbidden = []
        self.error = False

    def load(self, number):
        self.forbidden.append("approval")
        raise AssertionError("recover must not load/create approval")

    def verify(self, *args):
        self.forbidden.append("verify")
        raise AssertionError("recover cannot request fresh write authorization")

    def writer(self, authorize):
        def send(intent):
            self.forbidden.append("send")
            raise AssertionError("recover cannot send")
        def readback(intent):
            self.readbacks.append(copy.deepcopy(intent))
            self.assertEqual(self.intent, intent)
            # Independent descriptor must find the canonical merge lock held.
            with open(str(self.path) + ".merge-lock", "a") as probe:
                with self.assertRaises(BlockingIOError):
                    fcntl.flock(probe, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if self.error: raise OSError("synthetic unreadable remote outcome")
            return copy.deepcopy(self.remote)
        return types.SimpleNamespace(send=send, readback=readback)

    def reserve(self):
        journal = ReleaseJournal(self.db)
        self.assertIs(journal.reserve(copy.deepcopy(self.record)), True)

    def recover(self, number=15):
        result = operator()("recover", number, self.common, self)
        self.assertTrue(result["reason"])
        self.assertEqual([], self.forbidden, "no approval/verify/send on recovery")
        return result

    def queued(self):
        fresh = Queue(str(self.path))
        return fresh.numbers(), fresh.head_of(15)

    def test_no_journal_or_matching_reservation_holds_without_creation(self):
        self.assertEqual("hold", self.recover()["action"])
        self.assertFalse(self.db.exists(), "read-only command cannot initialize journal")
        self.assertEqual(([15], HEAD), self.queued())
        journal = ReleaseJournal(self.db)
        before = self.db.read_bytes()
        self.assertEqual("hold", self.recover()["action"])
        self.assertIsNone(journal.load(15))
        self.assertEqual(before, self.db.read_bytes())
        self.assertEqual([], self.readbacks)
        self.assertFalse((self.directory / "approvals").exists())

    def test_reserved_exact_readback_confirms_and_clears_without_approval(self):
        self.reserve()
        self.remote = dict(self.intent, merged=True, target_contains_merge=True, merge_sha=MERGE)
        result = self.recover()
        self.assertEqual("confirmed", result["action"])
        self.assertEqual(MERGE, result["merge_sha"])
        self.assertEqual([self.intent], self.readbacks)
        self.assertEqual(([], None), self.queued())
        self.assertEqual(dict(self.record, status="confirmed", merge_sha=MERGE), ReleaseJournal(self.db).load(15))
        self.assertFalse((self.directory / "approvals").exists())
        self.assertEqual("hold", self.recover()["action"], "no matching FIFO remains")
        self.assertEqual([self.intent], self.readbacks)

    def test_unknown_inexact_or_unreadable_proof_preserves_reservation_on_restart(self):
        self.reserve()
        exact = dict(self.intent, merged=True, target_contains_merge=True, merge_sha=MERGE)
        variants = [None, dict(exact, head=OTHER), dict(exact, base=OTHER),
                    dict(exact, target="topic"), dict(exact, number=16), dict(exact, merged=False),
                    dict(exact, target_contains_merge=False), dict(exact, merge_sha="bad")]
        for proof in variants:
            with self.subTest(proof=proof):
                self.remote = proof
                self.assertEqual("hold", self.recover()["action"])
                self.assertEqual(self.record, ReleaseJournal(self.db).load(15))
                self.assertEqual(([15], HEAD), self.queued())
        self.error = True
        self.assertEqual("hold", self.recover()["action"])
        self.assertEqual(self.record, ReleaseJournal(self.db).load(15))
        self.assertEqual(([15], HEAD), self.queued())
        self.assertEqual(len(variants) + 1, len(self.readbacks), "each explicit recovery may read, never resend")

    def test_wrong_fifo_or_mismatched_stamp_never_recovers_another_item(self):
        self.reserve()
        self.assertEqual("hold", self.recover(16)["action"])
        self.queue.set_head(15, OTHER)
        self.assertEqual("hold", self.recover()["action"])
        self.assertEqual([], self.readbacks)
        self.assertEqual(self.record, ReleaseJournal(self.db).load(15))
        self.assertEqual(([15], OTHER), self.queued())
        self.queue.remove(15)
        self.queue.enqueue(14)
        self.queue.enqueue(15)
        self.queue.set_head(15, HEAD)
        self.assertEqual("hold", self.recover()["action"])
        self.assertEqual([14, 15], self.queue.numbers())
        self.assertEqual([], self.readbacks)

    def test_confirmed_record_only_clears_exact_current_fifo_without_remote(self):
        self.reserve()
        confirmed = dict(self.record, status="confirmed", merge_sha=MERGE)
        self.assertIs(ReleaseJournal(self.db).confirm(confirmed), True)
        self.queue.set_head(15, OTHER)
        self.assertEqual("hold", self.recover()["action"])
        self.assertEqual(([15], OTHER), self.queued())
        self.queue.set_head(15, HEAD)
        result = self.recover()
        self.assertEqual("skip", result["action"])
        self.assertEqual(MERGE, result["merge_sha"])
        self.assertEqual(([], None), self.queued())
        self.assertEqual([], self.readbacks)
        self.assertEqual(confirmed, ReleaseJournal(self.db).load(15))


if __name__ == "__main__":
    unittest.main()
