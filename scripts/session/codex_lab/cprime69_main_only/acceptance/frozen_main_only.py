import copy
import importlib
import importlib.util
import unittest


HOLD = dict(action="HOLD", allow_reserve=False, allow_send=False)
RECOVER = dict(action="READY", allow_reserve=False, allow_send=False)
SEND = dict(action="READY", allow_reserve=True, allow_send=True)


class MainHTTPSAdmissionAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("lab_main_https") is not None:
            module = importlib.import_module("lab_main_https")
        function = getattr(module, "authorize", None)
        self.assertTrue(
            callable(function), "MAIN_HTTPS_ADMISSION_CAPABILITY_MISSING"
        )
        return function

    def fixture(self):
        permit = dict(
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
        )
        return permit, observed

    def check(self, function, permit, observed, recover_only, expected):
        before = copy.deepcopy((permit, observed))
        result = function(permit, observed, recover_only=recover_only)
        self.assertEqual(result, expected)
        self.assertEqual((permit, observed), before, "INPUT_MUTATED")

    def test_01_exact_main_scope_allows_only_explicit_new_send(self):
        function = self.seam()
        permit, observed = self.fixture()
        self.check(function, permit, observed, False, SEND)
        self.check(function, permit, observed, True, HOLD)

    def test_02_mismatched_missing_or_expired_evidence_holds(self):
        function = self.seam()
        mutations = (
            "repo_id", "repo", "ref", "app_id", "installation_id",
            "common_dir", "journal", "merge-lock", "sender-lock",
            "closure_sha256", "registry_sha256", "unadopted",
            "number", "head", "base", "target",
            "expiry", "after-expiry", "invalid-now",
        )
        for mutation in mutations:
            for recover_only in (False, True):
                with self.subTest(
                    mutation=mutation, recover_only=recover_only
                ):
                    permit, observed = self.fixture()
                    if mutation in (
                        "repo_id", "app_id", "installation_id"
                    ):
                        observed[mutation] += 1
                    elif mutation in (
                        "repo", "ref", "common_dir",
                        "closure_sha256", "registry_sha256",
                    ):
                        observed[mutation] = "wrong"
                    elif mutation == "journal":
                        observed["journal"]["inode"] += 1
                    elif mutation in ("merge-lock", "sender-lock"):
                        key = mutation.removesuffix("-lock")
                        observed["locks"][key]["inode"] += 1
                    elif mutation == "unadopted":
                        observed["registry_adopted"] = False
                    elif mutation in ("number", "head", "base", "target"):
                        observed["subject"][mutation] = (
                            68 if mutation == "number" else
                            "other" if mutation == "target" else "e" * 40
                        )
                    elif mutation == "expiry":
                        observed["now"] = permit["expires_at"]
                    elif mutation == "after-expiry":
                        observed["now"] = "2030-01-01T12:00:01Z"
                    else:
                        observed["now"] = "not-a-time"
                    self.check(
                        function, permit, observed, recover_only, HOLD
                    )

        # Matching evidence cannot authorize another repository/ref/install.
        for field, value in (
            ("repo_id", 1),
            ("repo", "other/repository"),
            ("ref", "refs/heads/other"),
            ("app_id", 1),
            ("installation_id", 1),
        ):
            with self.subTest(internally_consistent_wrong_scope=field):
                permit, observed = self.fixture()
                permit[field] = observed[field] = value
                self.check(function, permit, observed, False, HOLD)

        for field in (
            "journal", "locks", "closure_sha256", "registry_sha256",
            "subject", "registry_adopted", "now", "reservation",
        ):
            with self.subTest(missing=field):
                permit, observed = self.fixture()
                del observed[field]
                self.check(function, permit, observed, False, HOLD)

    def test_03_existing_reservation_is_recovery_only_and_never_resends(self):
        function = self.seam()
        permit, observed = self.fixture()
        observed["reservation"] = copy.deepcopy(permit["subject"])
        for recover_only in (False, True):
            self.check(
                function, permit, observed, recover_only, RECOVER
            )

        for field, value in (
            ("number", 68),
            ("head", "e" * 40),
            ("base", "f" * 40),
            ("target", "other"),
        ):
            for recover_only in (False, True):
                with self.subTest(
                    field=field, recover_only=recover_only
                ):
                    permit, observed = self.fixture()
                    observed["reservation"] = dict(
                        permit["subject"], **{field: value}
                    )
                    self.check(
                        function, permit, observed, recover_only, HOLD
                    )

        permit, observed = self.fixture()
        self.check(function, permit, observed, True, HOLD)


if __name__ == "__main__":
    unittest.main(verbosity=2)
