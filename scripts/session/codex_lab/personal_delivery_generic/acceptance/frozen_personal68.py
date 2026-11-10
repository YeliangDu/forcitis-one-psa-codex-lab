"""Independent personal PR68 operator boundary supplement.

Reuses the frozen_personal69 fixture shape without changing its assertions.
Contract:
  lab_personal68.authorize(permit, observed, *, recover_only)
    -> {"action": "READY"|"HOLD",
        "allow_reserve": bool, "allow_send": bool}

Existing consumed reservations permit read-only recovery, never another send.
Author has not executed this suite or touched formal state.

Dependency: unchanged frozen_personal69.py.
Missing-capability baseline:
  3 tests, 3 assertion failures, 0 errors, exit 1.
"""
import copy
import importlib
import importlib.util
import unittest

from frozen_personal69 import Personal69Acceptance


HOLD = dict(action="HOLD", allow_reserve=False, allow_send=False)
RECOVER = dict(action="READY", allow_reserve=False, allow_send=False)
SEND = dict(action="READY", allow_reserve=True, allow_send=True)


class Personal68Acceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("lab_personal68") is not None:
            module = importlib.import_module("lab_personal68")
        function = getattr(module, "authorize", None)
        self.assertTrue(
            callable(function), "PERSONAL68_CAPABILITY_MISSING"
        )
        return function

    def fixture(self, action="first-send"):
        permit, observed = Personal69Acceptance.fixture(self, action)
        permit["kind"] = observed["kind"] = "personal-pr70-v1"
        permit["subject"]["number"] = 70
        observed["subject"]["number"] = 70
        observed["fifo"] = [70]
        return permit, observed

    def check(self, function, permit, observed, recover_only, expected):
        before = copy.deepcopy((permit, observed))
        self.assertEqual(
            function(permit, observed, recover_only=recover_only),
            expected,
        )
        self.assertEqual((permit, observed), before, "INPUT_MUTATED")

    def test_01_only_exact_pr68_single_card_first_send_is_ready(self):
        function = self.seam()
        permit, observed = self.fixture()
        self.check(function, permit, observed, False, SEND)
        self.check(function, permit, observed, True, HOLD)

        # Internally consistent PR69 permission remains outside this seam.
        old_permit, old_observed = Personal69Acceptance.fixture(self)
        self.check(function, old_permit, old_observed, False, HOLD)
        self.check(function, old_permit, old_observed, True, HOLD)

    def test_02_changed_binding_queue_or_missing_exclusivity_holds(self):
        function = self.seam()
        for side in ("permit", "observed"):
            for field, value in (
                ("number", 69),
                ("head", "e" * 40),
                ("base", "f" * 40),
                ("target", "other"),
            ):
                with self.subTest(side=side, field=field):
                    permit, observed = self.fixture()
                    selected = permit if side == "permit" else observed
                    selected["subject"][field] = value
                    self.check(function, permit, observed, False, HOLD)

        for fifo in ([], [69], [70, 69], [70, 70]):
            with self.subTest(fifo=fifo):
                permit, observed = self.fixture()
                observed["fifo"] = fifo
                self.check(function, permit, observed, False, HOLD)

        for mutation in ("exclusive", "conflict", "expiry", "recovery-permit"):
            with self.subTest(mutation=mutation):
                permit, observed = self.fixture()
                if mutation == "exclusive":
                    observed["coordinator_exclusive"] = False
                elif mutation == "conflict":
                    observed["conflict"] = True
                elif mutation == "expiry":
                    observed["now"] = permit["expires_at"]
                else:
                    permit["action"] = observed["action"] = "recover-only"
                self.check(function, permit, observed, False, HOLD)

    def test_03_consumed_exact_reservation_only_recovers_without_send(self):
        function = self.seam()
        for action in ("first-send", "recover-only"):
            for recover_only in (False, True):
                with self.subTest(action=action, recover_only=recover_only):
                    permit, observed = self.fixture(action)
                    observed["reservation"] = copy.deepcopy(permit["subject"])
                    observed["fifo"] = []
                    self.check(
                        function, permit, observed, recover_only, RECOVER
                    )

        for field, value in (
            ("number", 69),
            ("head", "e" * 40),
            ("base", "f" * 40),
            ("target", "other"),
        ):
            with self.subTest(reservation_field=field):
                permit, observed = self.fixture("recover-only")
                observed["reservation"] = dict(
                    permit["subject"], **{field: value}
                )
                observed["fifo"] = []
                self.check(function, permit, observed, True, HOLD)

        permit, observed = self.fixture("recover-only")
        self.check(function, permit, observed, False, HOLD)
        self.check(function, permit, observed, True, HOLD)


if __name__ == "__main__":
    unittest.main(verbosity=2)
