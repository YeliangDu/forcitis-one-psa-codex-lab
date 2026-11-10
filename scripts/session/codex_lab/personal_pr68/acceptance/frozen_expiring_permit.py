import copy
from datetime import datetime, timedelta, timezone
import importlib
import importlib.util
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest


class SendPermitAcceptance(unittest.TestCase):
    def seam(self):
        directory = str(Path(__file__).resolve().parents[3] / "scripts/merge")
        if directory not in sys.path:
            sys.path.insert(0, directory)
        module = None
        if importlib.util.find_spec("ordinary_first_card") is not None:
            module = importlib.import_module("ordinary_first_card")
        function = getattr(module, "send_permit_guard", None)
        self.assertTrue(
            callable(function), "SEND_PERMIT_GUARD_CAPABILITY_MISSING"
        )
        return function

    def fixture(self, factory):
        expiry = datetime(2030, 1, 1, 12, 0, tzinfo=timezone.utc)
        permit = dict(
            kind="ordinary-first-card",
            subject=dict(
                number=69, head="c" * 40, base="b" * 40, target="main"
            ),
            expires_at="2030-01-01T12:00:00Z",
            permit_id="synthetic-single-send-permit",
        )
        state = dict(
            now=expiry - timedelta(seconds=1),
            installed=copy.deepcopy(permit),
            reads=0,
        )

        def reread():
            state["reads"] += 1
            return copy.deepcopy(state["installed"])

        guard = factory(permit, reread, lambda: state["now"])
        self.assertTrue(callable(guard))
        return permit, state, expiry, guard

    def test_01_guard_rereads_exact_permit_and_rejects_expiry_boundary(self):
        factory = self.seam()
        permit, state, expiry, guard = self.fixture(factory)
        original = copy.deepcopy(permit)

        self.assertIs(guard(), True)
        first_reads = state["reads"]
        self.assertGreater(first_reads, 0)

        # Entry was valid; time spent waiting consumes the remaining lifetime.
        state["now"] = expiry
        self.assertIs(guard(), False)
        state["now"] = expiry + timedelta(microseconds=1)
        self.assertIs(guard(), False)

        state["now"] = expiry - timedelta(seconds=1)
        for installed in (
            None,
            dict(copy.deepcopy(permit), permit_id="replacement-permit"),
            dict(copy.deepcopy(permit),
                 expires_at="2030-01-01T13:00:00Z"),
        ):
            with self.subTest(installed=installed):
                state["installed"] = installed
                self.assertIs(guard(), False)

        changed = copy.deepcopy(permit)
        changed["subject"]["head"] = "e" * 40
        state["installed"] = changed
        self.assertIs(guard(), False)

        state["installed"] = copy.deepcopy(permit)
        before_reads = state["reads"]
        self.assertIs(guard(), True)
        self.assertGreater(state["reads"], before_reads)
        self.assertEqual(permit, original)

    def test_02_actual_git_port_checks_guard_before_push_but_allows_reads(self):
        factory = self.seam()
        _, state, expiry, guard = self.fixture(factory)
        self.assertIs(guard(), True)

        production = importlib.import_module(
            "mq_lab_operator"
        ).ProductionPorts
        ports = production.__new__(production)
        calls = []

        def shell(argv):
            calls.append(copy.deepcopy(argv))
            return SimpleNamespace(returncode=0, stdout="synthetic-read\n")

        ports.sh = shell
        ports._send_permit_check = guard

        # Simulate lock/network waiting after the initial valid entry check.
        state["now"] = expiry
        with self.assertRaises(ValueError):
            ports.git([
                "push",
                "--force-with-lease=refs/heads/main:" + "b" * 40,
                "origin",
                "c" * 40 + ":refs/heads/main",
            ])
        self.assertEqual(calls, [], "EXPIRED_PERMIT_REACHED_REAL_SH")

        self.assertEqual(
            ports.git(["rev-parse", "HEAD"]), "synthetic-read\n"
        )
        self.assertEqual(calls, [["git", "rev-parse", "HEAD"]])


if __name__ == "__main__":
    unittest.main(verbosity=2)
