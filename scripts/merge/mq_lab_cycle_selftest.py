"""Issue #21 cycle contract, intended for scripts/merge/; hosted tests only.

Source parent: 09dc2796b4bd0cb002af450422b7ad8bb9c11383.
Minimal injected ports (all except runner_lock execute with that lock held):
 runner_lock(): exclusive non-nested context; failure means no other port calls.
 observe(): fresh dict repository, loaded, main, control_ok, child_stopped,
   next_intent (current independently approved identity or None), pointer, journal,
   journal_read: absent/found/unavailable, scoped to pointer number or current FIFO;
   health (action/head_sha/latest), fetched_sha, ancestry_ok, source_ok.
 persist_pointer(pointer): durable True before child; exact repository/loaded_base/
   number/head only, never approval or authority.
 once(intent): fresh one-shot canonical CLI, revalidates approval under merge lock;
   may mutate real journal/queue through that child only; return is NOT proof.
 recover(identity): readback-only canonical child, cannot send; uses existing row.
 refresh(old,new): verified fetch/check/main-reread/detach in dedicated checkout,
   returns True only for exact source switch; never executes successor code.
 retire_pointer(pointer): after exact successor source/main OR authoritative absent
   row, stopped child, same healthy predecessor and FIFO/head. Absent-row retirement
   ends this cycle with no request; a later cycle independently rereads admission.
   Unavailable is never absent. Retirement never deletes historical journal rows.
 run_cycle returns action hold/idle/attempted/recovered/restart, nonempty reason.
 No cached approval, pointer or child result substitutes for independent proof.
 Fake sequencing here is not the required two-real-disposable-release evidence.
"""
import copy
from contextlib import contextmanager
import importlib.util
from pathlib import Path
import unittest

OLD, NEW, SECOND = (c * 40 for c in "abc")
REPO = "YeliangDu/forcitis-one-psa-codex-lab"


def cycle():
    path = Path(__file__).with_name("mq_lab_cycle.py")
    if not path.exists(): return lambda ports: dict(action="hold", reason="no-bound-cycle")
    spec = importlib.util.spec_from_file_location("mq_cycle_acceptance", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.run_cycle


class InterruptedChild(BaseException):
    pass


class CycleAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.held = False
        self.lock_busy = False
        self.events = []
        self.pointer = None
        self.journal = None
        self.journal_read = "absent"
        self.historical_rows = []
        self.intent = dict(number=18, head="d" * 40, base=OLD, target="main")
        self.loaded = self.main = OLD
        self.control_ok = self.source_ok = self.ancestry_ok = True
        self.child_stopped = True
        self.health = dict(action="healthy", head_sha=OLD, latest=True)
        self.fetched_sha = NEW
        self.interrupt = None
        self.sends = 0
        self.refreshes = []
        self.observe_count = 0
        self.move_before_child = False

    @contextmanager
    def runner_lock(self):
        self.assertFalse(self.held, "one runner lock, no nested mq.py run")
        if self.lock_busy: raise OSError("synthetic duplicate supervisor")
        self.held = True
        try: yield
        finally: self.held = False

    def record(self, name):
        self.assertTrue(self.held, "all observation/children/source actions under same lock")
        self.events.append(name)

    def observe(self):
        self.record("observe")
        self.observe_count += 1
        if self.move_before_child and self.observe_count > 1: self.main = SECOND
        return copy.deepcopy(dict(repository=REPO, loaded=self.loaded, main=self.main,
            control_ok=self.control_ok, source_ok=self.source_ok, child_stopped=self.child_stopped,
            next_intent=self.intent, pointer=self.pointer, journal=self.journal, journal_read=self.journal_read,
            health=self.health, fetched_sha=self.fetched_sha, ancestry_ok=self.ancestry_ok))

    def persist_pointer(self, pointer):
        self.record("persist_pointer")
        self.assertEqual(dict(repository=REPO, loaded_base=self.loaded,
                              number=self.intent["number"], head=self.intent["head"]), pointer)
        self.assertIsNone(self.pointer)
        self.pointer = copy.deepcopy(pointer)
        return True

    def once(self, intent):
        self.record("once")
        self.assertIsNotNone(self.pointer, "durable pointer before one-shot child")
        self.assertEqual(self.intent, intent)
        if self.interrupt == "before-send": raise InterruptedChild()
        self.assertEqual(self.loaded, intent["base"])
        self.assertEqual(self.main, self.loaded)
        self.assertEqual(dict(action="healthy", head_sha=self.main, latest=True), self.health)
        self.journal = dict(intent, status="reserved", release_key="synthetic-key")
        self.journal_read = "found"
        self.sends += 1
        if self.interrupt == "after-send": raise InterruptedChild()
        merged = NEW if intent["base"] == OLD else SECOND
        self.journal.update(status="confirmed", merge_sha=merged)
        self.main = merged
        self.health = dict(action="healthy", head_sha=merged, latest=True)
        self.intent = None  # Child may clear queue before parent receives output.
        if self.interrupt == "after-confirm": raise InterruptedChild()
        return dict(action="confirmed", merge_sha=merged)

    def recover(self, identity):
        self.record("recover")
        self.assertEqual({k: self.journal[k] for k in ("number", "head", "base", "target")}, identity)
        self.assertEqual("reserved", self.journal["status"])
        return dict(action="hold", reason="synthetic-unknown-readback")

    def refresh(self, old, new):
        self.record("refresh")
        self.assertTrue(self.child_stopped and self.control_ok and self.source_ok)
        self.assertEqual((OLD, NEW), (old, new))
        self.assertEqual(NEW, self.main)
        self.assertEqual(NEW, self.health["head_sha"])
        self.assertEqual(NEW, self.fetched_sha)
        self.assertTrue(self.ancestry_ok)
        self.refreshes.append((old, new))
        return True  # Old process remains loaded OLD until a distinct invocation.

    def retire_pointer(self, pointer):
        self.record("retire_pointer")
        self.assertEqual(self.pointer, pointer)
        self.assertTrue(self.child_stopped and self.control_ok and self.source_ok)
        self.assertEqual(dict(action="healthy", head_sha=self.main, latest=True), self.health)
        if self.journal_read == "absent":
            self.assertIsNone(self.journal)
            self.assertEqual(pointer["loaded_base"], self.loaded)
            self.assertEqual(self.loaded, self.main)
            self.assertIsNotNone(self.intent)
            self.assertEqual(pointer["number"], self.intent["number"])
            self.assertEqual(pointer["head"], self.intent["head"])
        else:
            self.assertEqual("found", self.journal_read)
            self.assertIsNotNone(self.journal)
            self.assertEqual("confirmed", self.journal["status"])
            self.assertEqual(self.journal["merge_sha"], self.loaded)
            self.assertEqual(self.loaded, self.main)
            self.historical_rows.append(copy.deepcopy(self.journal))
        self.pointer = None
        # Model the next lookup (new FIFO number), not deletion of durable history.
        self.journal = None
        self.journal_read = "absent"
        return True

    def run_cycle(self):
        result = cycle()(self)
        self.assertFalse(self.held)
        self.assertTrue(result["reason"])
        return result

    def confirmed_fixture(self):
        self.pointer = dict(repository=REPO, loaded_base=OLD, number=18, head="d" * 40)
        self.journal = dict(number=18, head="d" * 40, base=OLD, target="main",
                            status="confirmed", release_key="synthetic-key", merge_sha=NEW)
        self.main = NEW
        self.journal_read = "found"
        self.health = dict(action="healthy", head_sha=NEW, latest=True)
        self.intent = dict(number=19, head="e" * 40, base=NEW, target="main")

    def test_pointer_persisted_before_one_shot_and_fresh_source_recheck(self):
        self.run_cycle()
        self.assertEqual(1, self.sends)
        self.assertLess(self.events.index("persist_pointer"), self.events.index("once"))
        self.assertGreaterEqual(self.observe_count, 2)
        self.assertEqual(18, self.pointer["number"])
        self.assertEqual(1, self.events.count("once"))

    def test_absent_row_retires_stopped_pointer_then_next_cycle_rereads_admission(self):
        self.interrupt = "before-send"
        try: self.run_cycle()
        except InterruptedChild: pass
        self.assertIsNotNone(self.pointer)
        self.assertIsNone(self.journal)
        self.assertEqual("absent", self.journal_read)
        self.events = []
        self.run_cycle()
        self.assertIn("retire_pointer", self.events)
        self.assertIsNone(self.pointer)
        self.assertEqual(0, self.sends)
        self.assertNotIn("once", self.events)
        self.assertEqual([], self.refreshes)
        self.interrupt = None
        previous_observations = self.observe_count
        self.run_cycle()
        self.assertGreater(self.observe_count, previous_observations)
        self.assertEqual(1, self.sends)

    def test_unavailable_row_or_unsafe_absent_pointer_holds_without_retirement(self):
        for mutation in ("unavailable", "child-live", "main-moved", "unhealthy", "fifo", "head"):
            with self.subTest(mutation=mutation):
                self.setUp()
                self.pointer = dict(repository=REPO, loaded_base=OLD, number=18, head="d" * 40)
                if mutation == "unavailable": self.journal_read = "unavailable"
                elif mutation == "child-live": self.child_stopped = False
                elif mutation == "main-moved": self.main = NEW
                elif mutation == "unhealthy": self.health = None
                elif mutation == "fifo": self.intent["number"] = 19
                else: self.intent["head"] = "f" * 40
                before = copy.deepcopy(self.pointer)
                self.assertEqual("hold", self.run_cycle()["action"])
                self.assertEqual(before, self.pointer)
                self.assertNotIn("retire_pointer", self.events)
                self.assertNotIn("recover", self.events)
                self.assertEqual(0, self.sends)
                self.assertEqual([], self.refreshes)

    def test_no_pointer_requires_exact_current_health_before_any_new_request(self):
        for health in (None, {}, dict(action="hold", head_sha=OLD, latest=True),
                       dict(action="healthy", head_sha=NEW, latest=True),
                       dict(action="healthy", head_sha=OLD, latest=False)):
            with self.subTest(health=health):
                self.setUp()
                self.health = health
                self.assertEqual("hold", self.run_cycle()["action"])
                self.assertEqual(0, self.sends)
                self.assertNotIn("once", self.events)
                self.assertNotIn("persist_pointer", self.events)
                self.assertEqual([], self.refreshes)

    def test_interrupted_reserved_restart_only_recovers_without_send_or_refresh(self):
        self.interrupt = "after-send"
        try: self.run_cycle()
        except InterruptedChild: pass
        self.assertIsNotNone(self.journal)
        self.assertEqual("reserved", self.journal["status"])
        before = copy.deepcopy(self.journal)
        self.events = []
        self.run_cycle()
        self.assertIn("recover", self.events)
        self.assertNotIn("once", self.events)
        self.assertEqual(1, self.sends)
        self.assertEqual(before, self.journal)
        self.assertEqual([], self.refreshes)

    def test_confirmed_before_output_restart_refreshes_but_old_process_never_releases_second(self):
        self.interrupt = "after-confirm"
        try: self.run_cycle()
        except InterruptedChild: pass
        self.assertIsNotNone(self.journal)
        self.assertEqual("confirmed", self.journal["status"])
        self.intent = dict(number=19, head="e" * 40, base=NEW, target="main")
        self.events = []
        self.assertEqual("restart", self.run_cycle()["action"])
        self.assertEqual([(OLD, NEW)], self.refreshes)
        self.assertNotIn("once", self.events)
        self.assertEqual(1, self.sends)
        self.assertIsNotNone(self.pointer)

    def test_successor_source_retires_checkpoint_before_consuming_own_new_base_proof(self):
        self.confirmed_fixture()
        self.loaded = NEW
        self.run_cycle()
        self.assertIn("retire_pointer", self.events)
        # One cycle can retire only; a later one must still use the new source.
        if self.sends == 0:
            self.run_cycle()
        self.assertEqual(1, self.sends)
        self.assertEqual(19, self.pointer["number"])
        self.assertEqual(NEW, self.pointer["loaded_base"])
        self.assertIsNotNone(self.journal)
        self.assertEqual(NEW, self.journal["base"])
        self.assertEqual(1, len(self.historical_rows))
        self.assertEqual(18, self.historical_rows[0]["number"])
        self.assertLess(self.events.index("retire_pointer"), self.events.index("once"))

    def test_bad_health_dirty_source_ancestry_or_pointer_mismatch_holds(self):
        for mutation in ("health-missing", "health-red", "health-stale", "dirty", "source",
                         "ancestry", "fetched", "child-live", "pointer"):
            with self.subTest(mutation=mutation):
                self.setUp()
                self.confirmed_fixture()
                if mutation == "health-missing": self.health = None
                elif mutation == "health-red": self.health["action"] = "hold"
                elif mutation == "health-stale": self.health["head_sha"] = OLD
                elif mutation == "dirty": self.control_ok = False
                elif mutation == "source": self.source_ok = False
                elif mutation == "ancestry": self.ancestry_ok = False
                elif mutation == "fetched": self.fetched_sha = SECOND
                elif mutation == "child-live": self.child_stopped = False
                else: self.pointer["head"] = "f" * 40
                self.assertEqual("hold", self.run_cycle()["action"])
                self.assertEqual(0, self.sends)
                self.assertEqual([], self.refreshes)

    def test_duplicate_lock_and_main_movement_before_child_make_zero_requests(self):
        self.lock_busy = True
        self.assertEqual("hold", self.run_cycle()["action"])
        self.assertEqual([], self.events)
        self.lock_busy = False
        self.move_before_child = True
        self.assertEqual("hold", self.run_cycle()["action"])
        self.assertEqual(0, self.sends)
        self.assertNotIn("once", self.events)


if __name__ == "__main__":
    unittest.main()
