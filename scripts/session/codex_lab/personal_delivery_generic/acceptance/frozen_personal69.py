import copy
import importlib
import importlib.util
import unittest


HOLD = dict(action="HOLD", allow_reserve=False, allow_send=False)
RECOVER = dict(action="READY", allow_reserve=False, allow_send=False)
SEND = dict(action="READY", allow_reserve=True, allow_send=True)


class Personal69Acceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("lab_personal69") is not None:
            module = importlib.import_module("lab_personal69")
        function = getattr(module, "authorize", None)
        self.assertTrue(
            callable(function), "PERSONAL69_CAPABILITY_MISSING"
        )
        return function

    def fixture(self, action="first-send"):
        permit = dict(
            kind="personal-first-card-v1",
            action=action,
            repo_id=1449415666,
            repo="YeliangDu/forcitis-one-psa-codex-lab",
            ref="refs/heads/main",
            app_id=5684707,
            installation_id=180252875,
            common_dir="/synthetic/isolated-lab/.git",
            journal=dict(device=1, inode=101),
            locks=dict(
                merge=dict(device=1, inode=102),
                sender=dict(device=1, inode=103),
            ),
            closure_sha256="a" * 64,
            registry_sha256="b" * 64,
            subject=dict(
                number=69, head="c" * 40, base="d" * 40, target="main"
            ),
            expires_at="2030-01-01T12:00:00Z",
        )
        observed = copy.deepcopy(permit)
        observed.update(
            now="2030-01-01T11:59:59Z",
            registry_adopted=True,
            reservation=None,
            coordinator_exclusive=True,
            fifo=[69],
            conflict=False,
        )
        return permit, observed

    def check(self, function, permit, observed, recover_only, expected):
        before = copy.deepcopy((permit, observed))
        self.assertEqual(
            function(permit, observed, recover_only=recover_only),
            expected,
        )
        self.assertEqual((permit, observed), before, "INPUT_MUTATED")

    def test_01_exact_first_send_requires_exclusive_single_card(self):
        function = self.seam()
        permit, observed = self.fixture()
        self.check(function, permit, observed, False, SEND)
        self.check(function, permit, observed, True, HOLD)

        for fifo in ([], [68], [69, 70], [69, 69]):
            with self.subTest(fifo=fifo):
                permit, observed = self.fixture()
                observed["fifo"] = fifo
                self.check(function, permit, observed, False, HOLD)

    def test_02_wrong_binding_missing_lock_conflict_or_expiry_holds(self):
        function = self.seam()
        mutations = (
            "kind", "action", "repo_id", "repo", "ref", "app_id",
            "installation_id", "common_dir", "journal", "locks",
            "merge-lock", "sender-lock", "closure_sha256",
            "registry_sha256", "registry-adoption", "exclusive",
            "conflict", "number", "head", "base", "target", "expiry",
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                permit, observed = self.fixture()
                if mutation in ("kind", "action"):
                    permit[mutation] = observed[mutation] = "other"
                elif mutation in (
                    "repo_id", "app_id", "installation_id"
                ):
                    observed[mutation] += 1
                elif mutation in (
                    "repo", "ref", "common_dir",
                    "closure_sha256", "registry_sha256",
                ):
                    observed[mutation] = "wrong"
                elif mutation in ("journal", "locks"):
                    del observed[mutation]
                elif mutation in ("merge-lock", "sender-lock"):
                    observed["locks"][
                        mutation.removesuffix("-lock")
                    ]["inode"] += 1
                elif mutation == "registry-adoption":
                    observed["registry_adopted"] = False
                elif mutation == "exclusive":
                    observed["coordinator_exclusive"] = False
                elif mutation == "conflict":
                    observed["conflict"] = True
                elif mutation in ("number", "head", "base", "target"):
                    observed["subject"][mutation] = (
                        68 if mutation == "number" else
                        "other" if mutation == "target" else "e" * 40
                    )
                else:
                    observed["now"] = permit["expires_at"]
                self.check(function, permit, observed, False, HOLD)

        # A consistent packet for another card is still outside this gate.
        permit, observed = self.fixture()
        permit["subject"]["number"] = observed["subject"]["number"] = 68
        observed["fifo"] = [68]
        self.check(function, permit, observed, False, HOLD)

    def test_03_exact_reservation_is_readonly_even_after_fifo_cleanup(self):
        function = self.seam()
        for action in ("first-send", "recover-only"):
            for recover_only in (False, True):
                with self.subTest(action=action, recover_only=recover_only):
                    permit, observed = self.fixture(action)
                    observed["reservation"] = copy.deepcopy(
                        permit["subject"]
                    )
                    observed["fifo"] = []
                    self.check(
                        function, permit, observed, recover_only, RECOVER
                    )

        # A recovery permit never authorizes creating a reservation or sending.
        permit, observed = self.fixture("recover-only")
        for recover_only in (False, True):
            self.check(function, permit, observed, recover_only, HOLD)

        for field, value in (
            ("number", 68), ("head", "e" * 40),
            ("base", "f" * 40), ("target", "other"),
        ):
            with self.subTest(reservation_field=field):
                permit, observed = self.fixture("recover-only")
                observed["reservation"] = dict(
                    permit["subject"], **{field: value}
                )
                observed["fifo"] = []
                self.check(function, permit, observed, True, HOLD)


if __name__ == "__main__":
    unittest.main(verbosity=2)
