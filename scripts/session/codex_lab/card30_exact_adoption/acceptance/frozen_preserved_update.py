"""Independent preserved-update acceptance, revised author/successor identity.

The previous suite remains unchanged.
Contract:
  successor_card.validate_preserved_update(scope, observed)
    -> {"action": "PRESERVED", "number": 68, "head": candidate_head}
  Rejection raises ValueError.

Original authorship remains author_session.
Observed stable execution continuity must match successor_session.
No I/O or actual PR68 operations. Not executed by author.

Missing-capability baseline:
  4 tests, 4 assertion failures, 0 errors, exit 1.
"""
import copy
import importlib
import importlib.util
import unittest


class PreservedUpdateAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("successor_card") is not None:
            module = importlib.import_module("successor_card")
        function = getattr(module, "validate_preserved_update", None)
        self.assertTrue(
            callable(function),
            "PRESERVED_UPDATE_CAPABILITY_MISSING",
        )
        return function

    def fixture(self):
        scope = dict(
            repository="YeliangDu/forcitis-one-psa-codex-lab",
            number=68,
            ref="refs/heads/codex/preserved-card67",
            author_session="11111111-1111-4111-8111-111111111111",
            successor_session="22222222-2222-4222-8222-222222222222",
            acceptance_sha256="a" * 64,
            old_B="1" * 40,
            old_H="2" * 40,
            new_B="3" * 40,
        )
        changes = [
            dict(
                path="fixture/logic.py",
                before_blob="5" * 40,
                after_blob="6" * 40,
                before_mode="100644",
                after_mode="100644",
            ),
            dict(
                path="fixture/logic_test.py",
                before_blob="7" * 40,
                after_blob="8" * 40,
                before_mode="100644",
                after_mode="100644",
            ),
        ]
        observed = dict(
            repository=scope["repository"],
            number=scope["number"],
            ref=scope["ref"],
            author_session=scope["author_session"],
            acceptance_sha256=scope["acceptance_sha256"],
            old_B=scope["old_B"],
            old_H=scope["old_H"],
            new_B=scope["new_B"],
            candidate=dict(
                head="4" * 40,
                parents=[scope["old_H"], scope["new_B"]],
            ),
            original_diff=dict(
                base=scope["old_B"], head=scope["old_H"],
                files=copy.deepcopy(changes),
            ),
            candidate_diff=dict(
                base=scope["new_B"], head="4" * 40,
                files=copy.deepcopy(changes),
            ),
            role_continuity=dict(
                phase="stable",
                previous_session_id=scope["successor_session"],
                current_session_id=scope["successor_session"],
                observed=True,
            ),
            pending_effects=[],
        )
        return scope, observed

    def reject(self, function, scope, observed):
        before = copy.deepcopy((scope, observed))
        with self.assertRaises(ValueError):
            function(scope, observed)
        self.assertEqual((scope, observed), before, "INPUT_MUTATED")

    def test_01_exact_two_parent_update_preserves_existing_work(self):
        function = self.seam()
        scope, observed = self.fixture()
        self.assertNotEqual(
            scope["author_session"], scope["successor_session"]
        )
        before = copy.deepcopy((scope, observed))
        self.assertEqual(
            function(scope, observed),
            dict(action="PRESERVED", number=68, head="4" * 40),
        )
        self.assertEqual((scope, observed), before)

    def test_02_identity_parents_and_diff_endpoints_are_exact(self):
        function = self.seam()
        mutations = (
            "repository", "number", "ref", "author_session",
            "acceptance_sha256", "old_B", "old_H", "new_B",
            "successor-claims-authorship",
            "reversed-parents", "missing-parent", "extra-parent",
            "original-base", "original-head",
            "candidate-base", "candidate-head",
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                scope, observed = self.fixture()
                if mutation in (
                    "repository", "number", "ref", "author_session",
                    "acceptance_sha256", "old_B", "old_H", "new_B",
                ):
                    observed[mutation] = (
                        69 if mutation == "number" else "wrong"
                    )
                elif mutation == "successor-claims-authorship":
                    observed["author_session"] = scope["successor_session"]
                elif mutation == "reversed-parents":
                    observed["candidate"]["parents"].reverse()
                elif mutation == "missing-parent":
                    observed["candidate"]["parents"].pop()
                elif mutation == "extra-parent":
                    observed["candidate"]["parents"].append("9" * 40)
                else:
                    side, endpoint = mutation.split("-")
                    key = (
                        "original_diff" if side == "original"
                        else "candidate_diff"
                    )
                    observed[key][endpoint] = "9" * 40
                self.reject(function, scope, observed)

    def test_03_changed_content_paths_modes_or_file_set_are_rejected(self):
        function = self.seam()
        for mutation in (
            "before_blob", "after_blob", "path",
            "before_mode", "after_mode",
            "extra-file", "missing-file", "duplicate-file",
        ):
            with self.subTest(mutation=mutation):
                scope, observed = self.fixture()
                files = observed["candidate_diff"]["files"]
                if mutation == "extra-file":
                    files.append(dict(
                        path="fixture/extra.py",
                        before_blob=None, after_blob="9" * 40,
                        before_mode=None, after_mode="100644",
                    ))
                elif mutation == "missing-file":
                    files.pop()
                elif mutation == "duplicate-file":
                    files.append(copy.deepcopy(files[0]))
                elif mutation == "path":
                    files[0]["path"] = "fixture/other.py"
                elif mutation.endswith("mode"):
                    files[0][mutation] = "100755"
                else:
                    files[0][mutation] = "9" * 40
                self.reject(function, scope, observed)

        scope, observed = self.fixture()
        observed["candidate_diff"]["files"][0]["after_blob"] = "f" * 40
        self.reject(function, scope, observed)

    def test_04_stable_observed_successor_and_no_pending_effects_required(self):
        function = self.seam()
        for mutation in (
            "unobserved", "provisional", "previous-session",
            "current-session", "author-as-previous",
            "author-as-current", "author-as-both",
            "pending", "missing-role", "missing-effects",
        ):
            with self.subTest(mutation=mutation):
                scope, observed = self.fixture()
                continuity = observed["role_continuity"]
                if mutation == "unobserved":
                    continuity["observed"] = False
                elif mutation == "provisional":
                    continuity["phase"] = "provisional"
                elif mutation in ("previous-session", "current-session"):
                    key = mutation.removesuffix("-session") + "_session_id"
                    continuity[key] = (
                        "33333333-3333-4333-8333-333333333333"
                    )
                elif mutation == "author-as-previous":
                    continuity["previous_session_id"] = scope["author_session"]
                elif mutation == "author-as-current":
                    continuity["current_session_id"] = scope["author_session"]
                elif mutation == "author-as-both":
                    continuity["previous_session_id"] = scope["author_session"]
                    continuity["current_session_id"] = scope["author_session"]
                elif mutation == "pending":
                    observed["pending_effects"] = [
                        dict(effect_id="synthetic-unknown", phase="unknown")
                    ]
                elif mutation == "missing-role":
                    del observed["role_continuity"]
                else:
                    del observed["pending_effects"]
                self.reject(function, scope, observed)


if __name__ == "__main__":
    unittest.main(verbosity=2)
