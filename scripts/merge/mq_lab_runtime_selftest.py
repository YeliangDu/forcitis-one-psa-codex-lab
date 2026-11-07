"""Canonical lifecycle acceptance with recorded queue and fake writer; hosted only."""
import copy
from contextlib import contextmanager
import importlib.util
from pathlib import Path
import unittest

HEAD, BASE, MERGE, OTHER = (c * 40 for c in "abcd")


def iteration():
    path = Path(__file__).with_name("mq_lab_runtime.py")
    if not path.exists():
        return lambda *args, **kwargs: dict(action="hold", reason="manual-runtime-disabled")
    spec = importlib.util.spec_from_file_location("mq_runtime_acceptance", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.run_iteration


class Ports:
    """Shared recorder: no nested lock, each queue/journal/remote I/O checked."""
    def __init__(self, case):
        self.case = case
        self.locked = False
        self.events = []
        self.members = [14, 27]
        self.heads = {14: HEAD, 27: OTHER}
        self.record = None
        self.reserve_ok = self.confirm_ok = True
        self.send_error = False
        self.proof = None
        self.changed_stamp = False
        self.evidence_fields = {}

    def io(self, name, *args):
        self.case.assertTrue(self.locked, "all I/O stays under the same merge lock")
        self.events.append((name, *args))

    @contextmanager
    def merge_lock(self):
        self.case.assertFalse(self.locked, "no nested merge lock")
        self.locked = True
        try:
            yield
        finally:
            self.locked = False

    def numbers(self):
        self.io("numbers")
        return list(self.members)

    def head_of(self, number):
        self.io("head_of", number)
        return self.heads.get(number)

    def remove(self, number):
        self.io("remove", number)
        self.case.assertEqual("confirmed", self.record["status"])
        self.case.assertEqual(self.record["head"], self.heads[number])
        self.members.remove(number)

    def drop_head(self, number):
        self.io("drop_head", number)
        self.case.assertEqual("confirmed", self.record["status"])
        del self.heads[number]

    def load(self, number):
        self.io("load", number)
        return copy.deepcopy(self.record)

    def reserve(self, record):
        self.io("reserve")
        self.case.assertIsNone(self.record)
        self.case.assertEqual("reserved", record["status"])
        if self.reserve_ok:
            self.record = copy.deepcopy(record)
        return self.reserve_ok

    def confirm(self, record):
        self.io("confirm")
        self.case.assertIn(("readback",), self.events)
        self.case.assertEqual("confirmed", record["status"])
        if self.confirm_ok:
            self.record = copy.deepcopy(record)
        return self.confirm_ok

    def evidence(self, number, head):
        self.io("evidence", number, head)
        if self.changed_stamp:
            self.heads[number] = OTHER
        return dict(number=number, head=head, base=BASE, target="main", action="eligible",
                    reason="synthetic-exact-proof", release_key="synthetic-key", **self.evidence_fields)

    def send(self, intent):
        self.io("send")
        self.case.assertEqual("reserved", self.record["status"])
        for field in ("number", "head", "base", "target", "release_key"):
            if field in intent:
                self.case.assertEqual(self.record[field], intent[field])
        if self.send_error:
            raise TimeoutError("synthetic unknown outcome")
        return dict(success=True)

    def readback(self, intent):
        self.io("readback")
        return copy.deepcopy(self.proof)


class MqLabRuntimeAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.ports = Ports(self)

    def exact_proof(self):
        return dict(number=14, head=HEAD, base=BASE, target="main", merged=True,
                    target_contains_merge=True, merge_sha=MERGE)

    def run_iteration(self, enabled=False):
        p = self.ports
        result = iteration()(p, p, p.evidence, p, allow_write=enabled)
        self.assertFalse(p.locked)
        self.assertTrue(result["reason"])
        return result

    def test_fifo_exact_admission_reserve_send_readback_confirm_then_clear(self):
        p = self.ports
        p.proof = self.exact_proof()
        result = self.run_iteration(True)
        self.assertEqual("confirmed", result["action"])
        self.assertEqual(MERGE, result["merge_sha"])
        self.assertEqual([27], p.members)
        self.assertEqual({27: OTHER}, p.heads)
        self.assertIn(("evidence", 14, HEAD), p.events)
        self.assertFalse(any(event[1:2] == (27,) for event in p.events))
        names = [event[0] for event in p.events]
        for before, after in (("reserve", "send"), ("send", "readback"),
                              ("readback", "confirm"), ("confirm", "remove"), ("confirm", "drop_head")):
            self.assertLess(names.index(before), names.index(after))
        self.assertEqual(1, names.count("send"))

    def test_disabled_mode_and_empty_queue_do_not_write(self):
        self.assertEqual("hold", self.run_iteration()["action"])
        self.assertEqual([14, 27], self.ports.members)
        self.assertFalse(any(e[0] in ("reserve", "send", "confirm", "remove", "drop_head") for e in self.ports.events))
        self.ports = Ports(self)
        self.ports.members = []
        self.assertEqual("idle", self.run_iteration(True)["action"])
        self.assertFalse(any(e[0] in ("evidence", "send", "reserve") for e in self.ports.events))

    def test_inexact_evidence_or_changed_stamp_cannot_send(self):
        for field, value in (("number", 15), ("head", OTHER), ("base", "bad"),
                             ("target", "topic"), ("release_key", ""), ("action", "hold"),
                             ("stamp", None)):
            with self.subTest(field=field):
                self.ports = Ports(self)
                original = self.ports.evidence
                def evidence(number, head):
                    result = original(number, head)
                    if field == "stamp":
                        self.ports.heads[number] = OTHER
                    else:
                        result[field] = value
                    return result
                self.ports.evidence = evidence
                self.assertEqual("hold", self.run_iteration(True)["action"])
                self.assertNotIn(("send",), self.ports.events)
                self.assertEqual([14, 27], self.ports.members)

    def test_unknown_transport_outcome_restart_only_reads_without_resend(self):
        self.ports.send_error = True
        self.assertEqual("hold", self.run_iteration(True)["action"])
        self.assertIsNotNone(self.ports.record, "unknown outcome must retain a durable reservation")
        self.assertEqual("reserved", self.ports.record["status"])
        self.assertIn(("readback",), self.ports.events)
        saved = copy.deepcopy(self.ports.record)
        self.ports = Ports(self)
        self.ports.record = saved
        self.assertEqual("hold", self.run_iteration(False)["action"])
        self.assertNotIn(("send",), self.ports.events)
        self.assertEqual(saved, self.ports.record)
        self.ports.proof = self.exact_proof()
        result = self.run_iteration(False)
        self.assertEqual("confirmed", result["action"])
        self.assertEqual(MERGE, result["merge_sha"])
        self.assertNotIn(("send",), self.ports.events)
        self.assertEqual([27], self.ports.members)

    def test_wrong_proof_or_failed_persistence_preserves_membership(self):
        for mutation in ("head", "base", "target", "merge_sha", "presence", "reserve", "confirm"):
            with self.subTest(mutation=mutation):
                self.ports = Ports(self)
                self.ports.proof = self.exact_proof()
                if mutation in ("head", "base"):
                    self.ports.proof[mutation] = OTHER
                elif mutation == "target": self.ports.proof["target"] = "topic"
                elif mutation == "merge_sha": self.ports.proof["merge_sha"] = "bad"
                elif mutation == "presence": self.ports.proof["target_contains_merge"] = False
                elif mutation == "reserve": self.ports.reserve_ok = False
                else: self.ports.confirm_ok = False
                self.assertEqual("hold", self.run_iteration(True)["action"])
                self.assertEqual([14, 27], self.ports.members)
                self.assertEqual({14: HEAD, 27: OTHER}, self.ports.heads)
                self.assertNotIn(("remove", 14), self.ports.events)
                if mutation == "reserve": self.assertNotIn(("send",), self.ports.events)

    def test_confirmed_record_clears_only_exact_current_stamp(self):
        record = dict(status="confirmed", number=14, head=HEAD, base=BASE,
                      target="main", release_key="synthetic-key", merge_sha=MERGE)
        for stale in (False, True):
            with self.subTest(stale=stale):
                self.ports = Ports(self)
                self.ports.record = copy.deepcopy(record)
                if stale: self.ports.heads[14] = OTHER
                result = self.run_iteration(False)
                self.assertEqual("hold" if stale else "skip", result["action"])
                self.assertEqual([14, 27] if stale else [27], self.ports.members)
                self.assertNotIn(("send",), self.ports.events)
                if not stale: self.assertEqual(MERGE, result["merge_sha"])


if __name__ == "__main__":
    unittest.main()
