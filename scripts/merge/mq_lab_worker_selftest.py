"""Finite canonical worker acceptance with fake ports; never starts a daemon."""
import copy
import importlib.util
from pathlib import Path
import unittest


def worker():
    path = Path(__file__).with_name("mq_lab_worker.py")
    if not path.exists(): return lambda *a, **k: None
    spec = importlib.util.spec_from_file_location("mq_worker_acceptance", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.run_worker


class WorkerAcceptanceTest(unittest.TestCase):
    def drive(self, states, answers=None, stop_initial=False, stop_after_emit=False):
        calls, events, writes = [], [], []
        state_index = 0
        stopped = stop_initial
        answers = answers or {}
        def read_queue():
            nonlocal state_index
            self.assertFalse(stopped, "no read after stop")
            calls.append(("read",))
            index = min(state_index, len(states) - 1)
            state_index += 1
            value = states[index]
            if isinstance(value, Exception): raise value
            return copy.deepcopy(value)
        def once(number):
            self.assertFalse(stopped, "no once after stop")
            calls.append(("once", number))
            writes.append(number)
            answer = answers.get(number, dict(number=number, action="hold", reason="synthetic-held"))
            if isinstance(answer, Exception): raise answer
            return copy.deepcopy(answer)
        def emit(event):
            nonlocal stopped
            self.assertFalse(stopped, "no emit after stop")
            calls.append(("emit",))
            self.assertIsInstance(event, dict)
            self.assertIn(event["action"], ("hold", "idle", "confirmed", "skip"))
            self.assertTrue(event["reason"])
            events.append(copy.deepcopy(event))
            if stop_after_emit: stopped = True
        def sleep(seconds):
            nonlocal stopped
            self.assertFalse(stopped, "no sleep after stop")
            self.assertGreater(seconds, 0)
            self.assertLessEqual(seconds, 10)
            calls.append(("sleep", seconds))
            if sum(c[0] == "sleep" for c in calls) >= len(states): stopped = True
        def stop():
            # An independent call budget fails instead of hanging hosted CI if a loop spins.
            self.assertLess(len(calls), 100, "worker must sleep/stop rather than spin")
            return stopped
        worker()(read_queue, once, emit, sleep, stop, interval=10)
        return calls, events, writes

    def test_fifo_only_and_hold_never_advances_to_second_item(self):
        calls, events, writes = self.drive([(True, [14, 27])] * 3)
        self.assertTrue(writes, "worker must inspect canonical FIFO through once")
        self.assertEqual({14}, set(writes))
        self.assertEqual(1, len(events), "same queue/action/reason emits once")
        self.assertEqual(14, events[0]["number"])
        self.assertEqual("hold", events[0]["action"])
        self.assertEqual(3, sum(c[0] == "sleep" for c in calls))

    def test_missing_and_initialized_empty_are_distinct_hold_and_idle(self):
        calls, events, writes = self.drive([(False, []), (True, []), (True, [])])
        self.assertEqual([], writes)
        self.assertEqual(["hold", "idle"], [e["action"] for e in events])
        self.assertEqual(3, sum(c[0] == "sleep" for c in calls))

    def test_confirmation_needs_fresh_queue_read_before_next_fifo(self):
        calls, events, writes = self.drive(
            [(True, [14, 27]), (True, [14, 27]), (True, [27]), (True, [])],
            {14: dict(action="confirmed", reason="exact-proof", merge_sha="a" * 40),
             27: dict(action="skip", reason="already-confirmed", merge_sha="b" * 40)})
        self.assertEqual([14, 14, 27], writes)
        self.assertEqual(["confirmed", "skip", "idle"], [e["action"] for e in events])
        for index, call in enumerate(calls):
            if call[0] == "once":
                self.assertEqual("read", calls[index - 1][0])

    def test_stop_before_read_and_immediately_after_emit(self):
        self.assertEqual(([], [], []), self.drive([(True, [14])], stop_initial=True))
        calls, events, writes = self.drive([(True, [14])], stop_after_emit=True)
        self.assertEqual([14], writes)
        self.assertEqual(1, len(events))
        self.assertEqual(("emit",), calls[-1])
        self.assertFalse(any(c[0] == "sleep" for c in calls))

    def test_invalid_or_unreadable_queue_never_calls_once(self):
        for state in ((True, [14, 14]), (True, [0]), (True, [True]),
                      (True, ["14"]), (True, None), (None, []),
                      RuntimeError("synthetic queue read failed")):
            with self.subTest(state=state):
                calls, events, writes = self.drive([state])
                self.assertEqual([], writes)
                self.assertEqual(1, len(events))
                self.assertEqual("hold", events[0]["action"])
                self.assertEqual(1, sum(c[0] == "sleep" for c in calls))

    def test_once_failure_holds_and_next_cycle_rereads_without_other_write(self):
        calls, events, writes = self.drive([(True, [14, 27]), (False, [])],
                                          {14: RuntimeError("synthetic operator failure")})
        self.assertEqual([14], writes)
        self.assertTrue(events)
        self.assertTrue(all(e["action"] == "hold" for e in events))
        self.assertEqual(2, sum(c[0] == "read" for c in calls))
        self.assertEqual(2, sum(c[0] == "sleep" for c in calls))


if __name__ == "__main__":
    unittest.main()
