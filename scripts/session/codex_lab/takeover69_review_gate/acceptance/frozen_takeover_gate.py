import copy
import importlib
import importlib.util
import unittest


class TakeoverGateAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("takeover_gate") is not None:
            module = importlib.import_module("takeover_gate")
        function = getattr(module, "plan", None)
        self.assertTrue(
            callable(function), "TAKEOVER_GATE_CAPABILITY_MISSING"
        )
        return function

    def fixture(self):
        identity = dict(number=69, head="c" * 40, base="b" * 40)
        packet = dict(
            **identity,
            C="d" * 40,
            authorization=dict(
                number=69, head=identity["head"], base=identity["base"],
                purpose="takeover-preparation",
                scope="operator-review-only",
            ),
            queue=dict(revision=7, sha256="1" * 64),
            journal=dict(sequence=11, sha256="2" * 64),
            lock=dict(state="free", generation=4),
        )
        observed = dict(
            **identity,
            trusted_current_C=dict(
                sha=packet["C"], trusted=True,
                head=identity["head"], base=identity["base"],
            ),
            old_sender=dict(state="terminal", authoritative=True),
            server_exclusive=None,
            queue=copy.deepcopy(packet["queue"]),
            journal=copy.deepcopy(packet["journal"]),
            lock=copy.deepcopy(packet["lock"]),
            unknown=[],
        )
        return packet, observed

    def check(self, function, packet, observed, expected):
        before = copy.deepcopy((packet, observed))
        self.assertEqual(
            function(packet, observed), {"action": expected}
        )
        self.assertEqual((packet, observed), before, "INPUT_MUTATED")

    def test_01_complete_evidence_only_opens_operator_review(self):
        function = self.seam()
        packet, observed = self.fixture()
        self.check(function, packet, observed, "READY_FOR_OPERATOR_REVIEW")

        # Server exclusivity is an alternative to authoritative termination.
        observed["old_sender"] = dict(
            state="unknown", authoritative=False
        )
        observed["server_exclusive"] = dict(
            held=True, authority="server",
            number=69, head=packet["head"], base=packet["base"],
        )
        self.check(function, packet, observed, "READY_FOR_OPERATOR_REVIEW")

    def test_02_missing_or_inconsistent_evidence_holds(self):
        function = self.seam()
        for mutation in (
            "missing-C", "untrusted-C", "wrong-C", "C-head", "C-base",
            "sender-running", "sender-untrusted", "client-exclusive",
            "wrong-exclusive-scope", "queue", "journal", "lock",
            "unknown", "authorization", "authorization-head",
            "authorization-scope",
        ):
            with self.subTest(mutation=mutation):
                packet, observed = self.fixture()
                if mutation == "missing-C":
                    del observed["trusted_current_C"]
                elif mutation == "untrusted-C":
                    observed["trusted_current_C"]["trusted"] = False
                elif mutation == "wrong-C":
                    observed["trusted_current_C"]["sha"] = "e" * 40
                elif mutation in ("C-head", "C-base"):
                    observed["trusted_current_C"][
                        mutation.removeprefix("C-")
                    ] = "e" * 40
                elif mutation == "sender-running":
                    observed["old_sender"]["state"] = "running"
                elif mutation == "sender-untrusted":
                    observed["old_sender"]["authoritative"] = False
                elif mutation in (
                    "client-exclusive", "wrong-exclusive-scope"
                ):
                    observed["old_sender"] = None
                    observed["server_exclusive"] = dict(
                        held=True, authority="server", number=69,
                        head=packet["head"], base=packet["base"],
                    )
                    if mutation == "client-exclusive":
                        observed["server_exclusive"]["authority"] = "client"
                    else:
                        observed["server_exclusive"]["number"] = 68
                elif mutation in ("queue", "journal", "lock"):
                    observed[mutation] = {}
                elif mutation == "unknown":
                    observed["unknown"] = ["synthetic-unresolved-effect"]
                elif mutation == "authorization":
                    del packet["authorization"]
                elif mutation == "authorization-head":
                    packet["authorization"]["head"] = "e" * 40
                else:
                    packet["authorization"]["scope"] = "write"
                self.check(function, packet, observed, "HOLD")

    def test_03_other_pr_or_changed_head_base_never_becomes_ready(self):
        function = self.seam()
        for side in ("packet", "observed"):
            for field, value in (
                ("number", 68), ("head", "e" * 40), ("base", "f" * 40)
            ):
                with self.subTest(side=side, field=field):
                    packet, observed = self.fixture()
                    target = packet if side == "packet" else observed
                    target[field] = value
                    self.check(function, packet, observed, "HOLD")

        # Even internally consistent evidence for PR68 is outside this gate.
        packet, observed = self.fixture()
        packet["number"] = observed["number"] = 68
        packet["authorization"]["number"] = 68
        self.check(function, packet, observed, "HOLD")


if __name__ == "__main__":
    unittest.main(verbosity=2)
