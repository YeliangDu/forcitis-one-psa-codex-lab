"""Single-CLI operator acceptance with real temporary queue/journal storage."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import sqlite3
import types
import unittest

from mq_queue import Queue
from mq_lab_journal_v2 import ReleaseJournalV2, V1, ALTER, BINDINGS

HEAD, BASE, MERGE = (c * 40 for c in "abc")


def operator():
    path = Path(__file__).with_name("mq_lab_operator.py")
    if not path.exists(): return lambda *a: dict(action="hold", reason="operator-not-bound")
    spec = importlib.util.spec_from_file_location("mq_operator_acceptance", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.run_command


class OperatorAcceptanceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.common = self.root / "forcitis-one-psa-codex-lab" / ".git"
        self.common.mkdir(parents=True)
        self.path = self.common / "merge-queue" / "merge.queue"
        self.intent = dict(number=15, head=HEAD, base=BASE, target="main")
        self.approval = dict(self.intent, repo_id=123, mode="normal")
        self.events = []
        self.red = False
        self.unknown = False
        self.remote_proof = None
        self.sends = 0

    def initialize(self):
        self.path.parent.mkdir(mode=0o700)
        self.path.parent.chmod(0o700)
        self.path.write_text("")
        journal = self.path.parent / "releases.sqlite3"
        with sqlite3.connect(journal) as db:
            db.execute(V1)
            db.execute(ALTER)
            db.execute(BINDINGS)
            db.execute("PRAGMA user_version=2")
        journal.chmod(0o600)

    def load(self, number):
        self.events.append(("load", number))
        return copy.deepcopy(self.approval)

    def verify(self, intent, approval):
        self.events.append(("verify", copy.deepcopy(intent)))
        if self.red or approval is None:
            return dict(action="hold", reason="synthetic-revoked")
        return dict(self.intent, action="eligible", reason="synthetic-proof", release_key="synthetic-key")

    def writer(self, authorize):
        self.events.append(("writer",))
        def send(intent):
            self.assertEqual(self.intent, intent)
            self.assertIs(authorize(intent), True, "write boundary revalidates exact proof")
            self.assertTrue(self.records(), "SQLite reserve precedes request")
            self.assertEqual("reserved", self.records()[0]["status"])
            self.sends += 1
            if self.unknown: raise TimeoutError("synthetic uncertain effect")
            return dict(success=True)
        def readback(intent):
            self.events.append(("readback", copy.deepcopy(intent)))
            self.assertEqual(self.intent, intent)
            return copy.deepcopy(self.remote_proof)
        return types.SimpleNamespace(send=send, readback=readback)

    def command(self, command, number=15):
        result = operator()(command, number, self.common, self)
        self.assertTrue(result["reason"])
        return result

    def files(self):
        return {str(p.relative_to(self.common)): p.read_bytes()
                for p in self.common.rglob("*") if p.is_file()}

    def records(self):
        records = []
        for path in self.common.rglob("*"):
            if path.is_file() and path.read_bytes()[:16] == b"SQLite format 3\x00":
                record = ReleaseJournalV2(path).load(15)
                if record is not None: records.append(record)
        return records

    def test_missing_queue_and_missing_approval_hold_without_initialization(self):
        for command in ("plan", "enqueue", "once"):
            self.assertEqual("hold", self.command(command)["action"])
            self.assertFalse(self.path.parent.exists())
        self.initialize()
        self.approval = None
        before = self.files()
        self.assertEqual("hold", self.command("enqueue")["action"])
        self.assertEqual([], Queue(str(self.path)).numbers())
        self.assertEqual(0, self.sends)
        self.assertEqual([], self.records())
        self.assertEqual(b"", self.path.read_bytes())

    def test_explicit_plan_is_readonly_and_does_not_construct_writer(self):
        self.initialize()
        before = self.files()
        result = self.command("plan")
        self.assertEqual("eligible", result["action"])
        for key, value in self.intent.items(): self.assertEqual(value, result[key])
        self.assertEqual(before, self.files())
        self.assertNotIn(("writer",), self.events)
        self.assertEqual(0, self.sends)

    def test_exact_enqueue_once_and_durable_confirmed_cleanup(self):
        self.initialize()
        self.assertEqual("admitted", self.command("enqueue")["action"])
        fresh = Queue(str(self.path))
        self.assertEqual([15], fresh.numbers())
        self.assertEqual(HEAD, fresh.head_of(15))
        self.remote_proof = dict(self.intent, merged=True, target_contains_merge=True, merge_sha=MERGE)
        result = self.command("once")
        self.assertEqual("confirmed", result["action"])
        self.assertEqual(MERGE, result["merge_sha"])
        self.assertEqual(1, self.sends)
        self.assertEqual([], fresh.numbers())
        self.assertIsNone(fresh.head_of(15))
        self.assertEqual(1, len(self.records()))
        self.assertEqual("confirmed", self.records()[0]["status"])
        self.assertEqual(MERGE, self.records()[0]["merge_sha"])

    def test_requested_number_cannot_release_another_fifo_item_and_red_holds(self):
        self.initialize()
        queue = Queue(str(self.path))
        queue.set_head(14, "d" * 40)
        queue.enqueue(14)
        self.assertEqual("admitted", self.command("enqueue")["action"])
        self.assertEqual([14, 15], queue.numbers())
        self.assertEqual("hold", self.command("once")["action"])
        self.assertEqual([14, 15], queue.numbers())
        self.assertEqual(0, self.sends)
        queue.remove(14)
        queue.drop_head(14)
        self.red = True
        self.assertEqual("hold", self.command("once")["action"])
        self.assertEqual([15], queue.numbers())
        self.assertEqual(0, self.sends)
        self.assertEqual([], self.records())

    def test_revoked_approval_recovers_existing_reservation_without_resend(self):
        self.initialize()
        self.assertEqual("admitted", self.command("enqueue")["action"])
        self.unknown = True
        self.assertEqual("hold", self.command("once")["action"])
        records = self.records()
        self.assertEqual(1, len(records), "uncertain request persists one reservation")
        self.assertEqual("reserved", records[0]["status"])
        self.assertEqual(1, self.sends)
        self.approval = None
        self.red = True
        self.assertEqual("hold", self.command("once")["action"])
        self.assertEqual(records, self.records())
        self.assertEqual([15], Queue(str(self.path)).numbers())
        self.remote_proof = dict(self.intent, merged=True, target_contains_merge=True, merge_sha=MERGE)
        self.assertEqual("confirmed", self.command("once")["action"])
        self.assertEqual(1, self.sends)
        self.assertEqual([], Queue(str(self.path)).numbers())
        self.assertIsNone(Queue(str(self.path)).head_of(15))
        self.assertEqual("confirmed", self.records()[0]["status"])


if __name__ == "__main__":
    unittest.main()
