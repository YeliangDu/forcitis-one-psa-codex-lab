import copy
from contextlib import contextmanager
import importlib
from pathlib import Path
import sys
import unittest


HEAD = "c" * 40
BASE = "b" * 40
MERGE = "d" * 40
INTENT = dict(number=69, head=HEAD, base=BASE, target="main")


def runtime():
    root = Path(__file__).resolve().parents[3]
    directory = str(root / "scripts" / "merge")
    if directory not in sys.path:
        sys.path.insert(0, directory)
    return importlib.import_module("mq_lab_runtime")


class Queue:
    def __init__(self):
        self.fifo = [69]
        self.stamps = {69: HEAD}

    @contextmanager
    def merge_lock(self):
        yield

    def numbers(self):
        return list(self.fifo)

    def head_of(self, number):
        return self.stamps.get(number)

    def remove(self, number):
        self.fifo.remove(number)

    def drop_head(self, number):
        self.stamps.pop(number, None)


class Journal:
    def __init__(self):
        self.record = dict(
            INTENT, status="reserved", release_key="synthetic-release-69"
        )

    def load(self, number):
        return copy.deepcopy(self.record)

    def confirm(self, record):
        self.record = copy.deepcopy(record)
        return True


class Writer:
    def __init__(self):
        self.reads = []
        self.sends = []

    def readback(self, intent):
        self.reads.append(copy.deepcopy(intent))
        return dict(
            INTENT,
            merged=True,
            target_contains_merge=True,
            merge_sha=MERGE,
        )

    def send(self, intent):
        self.sends.append(copy.deepcopy(intent))


class ReservedRecoveryHealthDiagnostic(unittest.TestCase):
    def test_unhealthy_new_main_retains_fifo_and_stamp_without_send(self):
        queue, journal, writer = Queue(), Journal(), Writer()
        health = dict(main=MERGE, healthy=False)

        def evidence(number, head):
            return dict(
                INTENT,
                action="hold",
                reason="new-main-health-not-green",
                health=copy.deepcopy(health),
            )

        runtime().run_iteration(
            queue, journal, evidence, writer, allow_write=False
        )

        # Establish actual reserved recovery, not a setup/path rejection.
        self.assertEqual(writer.reads, [INTENT])
        self.assertEqual(writer.sends, [])
        self.assertIs(health["healthy"], False)

        # One deliberately red assertion covers both retained admission facts.
        self.assertEqual(
            (queue.numbers(), queue.head_of(69)),
            ([69], HEAD),
            "UNHEALTHY_MAIN_MUST_RETAIN_FIFO_AND_STAMP",
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
