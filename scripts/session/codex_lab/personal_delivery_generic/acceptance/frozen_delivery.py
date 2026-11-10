import copy
import hashlib
import importlib
import importlib.util
import json
import unittest

from frozen_personal69 import Personal69Acceptance


HOLD = dict(action="HOLD", allow_reserve=False, allow_send=False)
RECOVER = dict(action="READY", allow_reserve=False, allow_send=False)
SEND = dict(action="READY", allow_reserve=True, allow_send=True)


def permit_digest(permit):
    raw = json.dumps(
        permit, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode()
    return hashlib.sha256(raw).hexdigest()


class PersonalDeliveryAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("lab_delivery") is not None:
            module = importlib.import_module("lab_delivery")
        function = getattr(module, "authorize", None)
        self.assertTrue(
            callable(function), "PERSONAL_DELIVERY_CAPABILITY_MISSING"
        )
        return function

    def fixture(self, number=68, head="c" * 40, base="d" * 40,
                action="first-send"):
        permit, observed = Personal69Acceptance.fixture(self, action)
        permit.update(
            kind="personal-delivery-v1",
            role=dict(
                name="one-lead",
                session_id="11111111-1111-4111-8111-111111111111",
            ),
        )
        permit["subject"].update(number=number, head=head, base=base)
        # Preserve independent observed scope fields with equal fixture values.
        observed.update(copy.deepcopy(permit))
        observed.update(
            fifo=[number],
            paused=False,
            permit_sha256=permit_digest(permit),
            metadata=dict(
                head=head, base=base,
                role=copy.deepcopy(permit["role"]),
                permit_sha256=permit_digest(permit),
            ),
            health=dict(action="healthy", head_sha=base),
            recovery_status=None,
        )
        return permit, observed

    def check(self, function, permit, observed, recover_only, expected):
        before = copy.deepcopy((permit, observed))
        self.assertEqual(
            function(permit, observed, recover_only=recover_only),
            expected,
        )
        self.assertEqual((permit, observed), before, "INPUT_MUTATED")

    def test_01_distinct_positive_prs_and_heads_share_one_closure(self):
        function = self.seam()
        closures = []
        for number, head, base in (
            (68, "c" * 40, "d" * 40),
            (137, "e" * 40, "f" * 40),
        ):
            with self.subTest(number=number):
                permit, observed = self.fixture(number, head, base)
                closures.append(permit["closure_sha256"])
                self.check(function, permit, observed, False, SEND)
                self.check(function, permit, observed, True, HOLD)
        self.assertEqual(closures[0], closures[1])

    def test_02_exact_consumed_reservation_never_authorizes_another_send(self):
        function = self.seam()
        for action in ("first-send", "recover-only"):
            for recover_only in (False, True):
                with self.subTest(action=action, recover_only=recover_only):
                    permit, observed = self.fixture(action=action)
                    observed["reservation"] = copy.deepcopy(permit["subject"])
                    observed["fifo"] = []
                    self.check(
                        function, permit, observed, recover_only, RECOVER
                    )
                    # Repeated notification / reconstructed caller input.
                    self.check(
                        function, copy.deepcopy(permit),
                        copy.deepcopy(observed), recover_only, RECOVER,
                    )

        permit, observed = self.fixture(action="recover-only")
        self.check(function, permit, observed, False, HOLD)
        self.check(function, permit, observed, True, HOLD)

    def test_03_waiting_health_or_unknown_reply_only_allows_readback(self):
        function = self.seam()
        for status in ("unknown", "awaiting-health"):
            with self.subTest(status=status):
                permit, observed = self.fixture()
                observed.update(
                    reservation=copy.deepcopy(permit["subject"]),
                    recovery_status=status,
                    health=dict(action="hold", head_sha=permit["subject"]["base"]),
                )
                for recover_only in (False, True):
                    self.check(
                        function, permit, observed, recover_only, RECOVER
                    )

        permit, observed = self.fixture()
        observed["health"]["action"] = "hold"
        self.check(function, permit, observed, False, HOLD)

        permit, observed = self.fixture()
        observed["reservation"] = dict(permit["subject"], head="9" * 40)
        self.check(function, permit, observed, True, HOLD)

    def test_04_expiry_pause_scope_fifo_and_metadata_mismatch_hold(self):
        function = self.seam()
        mutations = (
            "expired", "paused", "kind", "nonpositive", "boolean-number",
            "head", "base", "closure", "role", "permit-digest",
            "metadata-head", "metadata-base", "metadata-role",
            "metadata-permit", "fifo-empty", "fifo-other", "fifo-multiple",
            "exclusive", "conflict", "health-head",
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                permit, observed = self.fixture()
                if mutation == "expired":
                    observed["now"] = permit["expires_at"]
                elif mutation == "paused":
                    observed["paused"] = True
                elif mutation == "kind":
                    permit["kind"] = observed["kind"] = "personal-first-card-v1"
                elif mutation in ("nonpositive", "boolean-number"):
                    value = 0 if mutation == "nonpositive" else True
                    permit["subject"]["number"] = value
                    observed["subject"]["number"] = value
                    observed["fifo"] = [value]
                    observed["permit_sha256"] = permit_digest(permit)
                    observed["metadata"]["permit_sha256"] = permit_digest(permit)
                elif mutation in ("head", "base"):
                    observed["subject"][mutation] = "9" * 40
                elif mutation == "closure":
                    observed["closure_sha256"] = "9" * 64
                elif mutation == "role":
                    observed["role"]["session_id"] = (
                        "22222222-2222-4222-8222-222222222222"
                    )
                elif mutation == "permit-digest":
                    observed["permit_sha256"] = "9" * 64
                elif mutation.startswith("metadata-"):
                    key = mutation.removeprefix("metadata-")
                    if key == "role":
                        observed["metadata"]["role"]["name"] = "other-role"
                    else:
                        key = "permit_sha256" if key == "permit" else key
                        observed["metadata"][key] = "9" * (
                            64 if key == "permit_sha256" else 40
                        )
                elif mutation == "fifo-empty":
                    observed["fifo"] = []
                elif mutation == "fifo-other":
                    observed["fifo"] = [69]
                elif mutation == "fifo-multiple":
                    observed["fifo"] = [68, 69]
                elif mutation == "exclusive":
                    observed["coordinator_exclusive"] = False
                elif mutation == "conflict":
                    observed["conflict"] = True
                else:
                    observed["health"]["head_sha"] = "9" * 40
                self.check(function, permit, observed, False, HOLD)


if __name__ == "__main__":
    unittest.main(verbosity=2)
