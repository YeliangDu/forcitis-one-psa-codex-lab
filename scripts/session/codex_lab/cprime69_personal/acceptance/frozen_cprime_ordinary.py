import copy
from contextlib import contextmanager
import importlib
import importlib.util
import inspect
from pathlib import Path
import sys
import unittest


IDENTITY = dict(number=69, head="c" * 40, base="b" * 40, target="main")
MERGE = "d" * 40


def runtime_module():
    directory = str(Path(__file__).resolve().parents[3] / "scripts/merge")
    if directory not in sys.path:
        sys.path.insert(0, directory)
    return importlib.import_module("mq_lab_runtime")


class Queue:
    def __init__(self):
        self.fifo = [69, 70]
        self.stamps = {69: IDENTITY["head"], 70: "e" * 40}

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
    def __init__(self, phase="reserved"):
        self.record = None if phase is None else dict(
            IDENTITY, release_key="synthetic-release-69", status=phase
        )
        if phase == "confirmed":
            self.record["merge_sha"] = MERGE
        self.confirmations = []
        self.reservations = []

    def load(self, number):
        if self.record is None or self.record["number"] != number:
            return None
        return copy.deepcopy(self.record)

    def reserve(self, record):
        if self.record is not None:
            return False
        self.reservations.append(copy.deepcopy(record))
        self.record = copy.deepcopy(record)
        return True

    def confirm(self, record):
        self.confirmations.append(copy.deepcopy(record))
        self.record = copy.deepcopy(record)
        return True


class Writer:
    def __init__(self):
        self.reads = []
        self.sends = []

    def readback(self, intent):
        self.reads.append(copy.deepcopy(intent))
        return dict(
            IDENTITY, merged=True, target_contains_merge=True,
            merge_sha=MERGE,
        )

    def send(self, intent):
        self.sends.append(copy.deepcopy(intent))
        raise TimeoutError("synthetic lost send reply")


class OrdinaryMergeAcceptance(unittest.TestCase):
    def runtime(self):
        function = runtime_module().run_iteration
        parameter = inspect.signature(function).parameters.get("healthy")
        self.assertIsNotNone(parameter, "HEALTHY_API_CAPABILITY_MISSING")
        self.assertNotEqual(
            parameter.kind, inspect.Parameter.POSITIONAL_ONLY,
            "HEALTHY_API_CAPABILITY_MISSING",
        )
        return function

    def fixture(self, phase="reserved"):
        queue, journal, writer = Queue(), Journal(phase), Writer()
        evidence_calls = []

        def evidence(number, head):
            evidence_calls.append((number, head))
            return dict(
                IDENTITY, action="eligible", reason="synthetic-exact-proof",
                release_key="synthetic-release-69",
            )

        return queue, journal, writer, evidence, evidence_calls

    def good_health(self, calls):
        def healthy(merge_sha):
            calls.append(merge_sha)
            return dict(action="healthy", head_sha=merge_sha)
        return healthy

    def test_01_reserved_requires_current_exact_health_and_blocks_next_card(self):
        run = self.runtime()
        outcomes = (
            "missing", "none", "stale", "wrong-head", "unhealthy", "exception"
        )
        for outcome in outcomes:
            with self.subTest(outcome=outcome):
                queue, journal, writer, evidence, calls = self.fixture()
                original = copy.deepcopy(journal.record)

                def healthy(merge_sha):
                    self.assertEqual(merge_sha, MERGE)
                    if outcome == "exception":
                        raise RuntimeError("synthetic health unavailable")
                    if outcome == "none":
                        return None
                    return dict(
                        action=(
                            "stale" if outcome == "stale" else
                            "hold" if outcome == "unhealthy" else "healthy"
                        ),
                        head_sha="f" * 40 if outcome == "wrong-head" else MERGE,
                    )

                kwargs = {} if outcome == "missing" else {"healthy": healthy}
                result = run(
                    queue, journal, evidence, writer,
                    allow_write=False, **kwargs,
                )
                self.assertEqual(result["action"], "hold")
                self.assertEqual(queue.numbers(), [69, 70])
                self.assertEqual(queue.head_of(69), IDENTITY["head"])
                self.assertEqual(journal.record, original)
                self.assertEqual(journal.confirmations, [])
                self.assertEqual(writer.sends, [])
                self.assertEqual(writer.reads, [IDENTITY])
                self.assertEqual(calls, [])

    def test_02_exact_health_confirms_and_clears_only_current_card(self):
        run = self.runtime()
        queue, journal, writer, evidence, evidence_calls = self.fixture()
        health_calls = []
        run(
            queue, journal, evidence, writer, allow_write=False,
            healthy=self.good_health(health_calls),
        )
        self.assertEqual(health_calls, [MERGE])
        self.assertEqual(writer.reads, [IDENTITY])
        self.assertEqual(writer.sends, [])
        self.assertEqual(queue.numbers(), [70])
        self.assertIsNone(queue.head_of(69))
        self.assertEqual(queue.head_of(70), "e" * 40)
        self.assertEqual(len(journal.confirmations), 1)
        self.assertEqual(journal.record, dict(
            IDENTITY, release_key="synthetic-release-69",
            status="confirmed", merge_sha=MERGE,
        ))
        self.assertEqual(evidence_calls, [])

    def test_03_confirmed_record_also_requires_health_before_cleanup(self):
        run = self.runtime()
        queue, journal, writer, evidence, evidence_calls = self.fixture(
            "confirmed"
        )
        original = copy.deepcopy(journal.record)
        result = run(
            queue, journal, evidence, writer, allow_write=False,
            healthy=lambda merge_sha: dict(action="stale", head_sha=merge_sha),
        )
        self.assertEqual(result["action"], "hold")
        self.assertEqual(queue.numbers(), [69, 70])
        self.assertEqual(queue.head_of(69), IDENTITY["head"])
        self.assertEqual(journal.record, original)
        self.assertEqual(writer.sends, [])

        health_calls = []
        run(
            queue, journal, evidence, writer, allow_write=False,
            healthy=self.good_health(health_calls),
        )
        self.assertEqual(health_calls, [MERGE])
        self.assertEqual(queue.numbers(), [70])
        self.assertIsNone(queue.head_of(69))
        self.assertEqual(writer.sends, [])
        self.assertEqual(evidence_calls, [])

    def test_04_lost_send_reply_and_recovery_never_resend(self):
        run = self.runtime()
        queue, journal, writer, evidence, evidence_calls = self.fixture(None)
        result = run(
            queue, journal, evidence, writer, allow_write=True,
            healthy=lambda merge_sha: dict(action="hold", head_sha=merge_sha),
        )
        self.assertEqual(result["action"], "hold")
        self.assertEqual(len(journal.reservations), 1)
        self.assertEqual(journal.record["status"], "reserved")
        self.assertEqual(writer.sends, [IDENTITY])
        self.assertEqual(queue.numbers(), [69, 70])

        # Another uncertain recovery is read-only and retains the stamp.
        run(
            queue, journal, evidence, writer, allow_write=False,
            healthy=lambda merge_sha: dict(action="stale", head_sha=merge_sha),
        )
        self.assertEqual(writer.sends, [IDENTITY])
        self.assertEqual(queue.head_of(69), IDENTITY["head"])

        run(
            queue, journal, evidence, writer, allow_write=False,
            healthy=self.good_health([]),
        )
        self.assertEqual(writer.sends, [IDENTITY])
        self.assertEqual(queue.numbers(), [70])
        self.assertEqual(len(journal.confirmations), 1)
        self.assertEqual(evidence_calls, [(69, IDENTITY["head"])])

    def test_05_launcher_accepts_only_exact_authorized_once_for_pr69(self):
        module = None
        if importlib.util.find_spec("ordinary_first_card") is not None:
            module = importlib.import_module("ordinary_first_card")
        authorize = getattr(module, "authorize", None)
        self.assertTrue(
            callable(authorize), "FIRST_CARD_LAUNCHER_CAPABILITY_MISSING"
        )
        request = dict(IDENTITY, command="once")
        approval = dict(
            kind="ordinary-first-card",
            subject=copy.deepcopy(IDENTITY),
            verification=dict(
                source="installed-local-authorization", verified=True
            ),
        )
        before = copy.deepcopy((request, approval))
        self.assertEqual(authorize(request, approval), IDENTITY)
        self.assertEqual((request, approval), before)

        for mutation in (
            "command", "number", "head", "base", "target",
            "kind", "approval-number", "approval-head", "approval-base",
            "approval-target", "missing-verification", "unverified",
            "wrong-source",
        ):
            with self.subTest(mutation=mutation):
                req, app = copy.deepcopy(before)
                if mutation == "command":
                    req["command"] = "loop"
                elif mutation in ("number", "head", "base", "target"):
                    req[mutation] = (
                        68 if mutation == "number" else
                        "other" if mutation == "target" else "f" * 40
                    )
                elif mutation == "kind":
                    app["kind"] = "other"
                elif mutation.startswith("approval-"):
                    field = mutation.removeprefix("approval-")
                    app["subject"][field] = (
                        68 if field == "number" else
                        "other" if field == "target" else "f" * 40
                    )
                elif mutation == "missing-verification":
                    del app["verification"]
                elif mutation == "unverified":
                    app["verification"]["verified"] = False
                else:
                    app["verification"]["source"] = "caller-claim"
                unchanged = copy.deepcopy((req, app))
                with self.assertRaises(ValueError):
                    authorize(req, app)
                self.assertEqual((req, app), unchanged)


if __name__ == "__main__":
    unittest.main(verbosity=2)
