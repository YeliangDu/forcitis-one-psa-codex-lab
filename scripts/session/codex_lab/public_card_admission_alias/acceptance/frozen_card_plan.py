import copy
import importlib
import importlib.util
import unittest


LAB_REPO_ID = 1449415666


class OwnedPlanAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("card_plan") is not None:
            module = importlib.import_module("card_plan")
        function = getattr(module, "authorize", None)
        self.assertTrue(callable(function), "CARD_PLAN_CAPABILITY_MISSING")
        return function

    def fixture(self):
        author = "11111111-1111-4111-8111-111111111111"
        reviewer = "22222222-2222-4222-8222-222222222222"
        quality = "33333333-3333-4333-8333-333333333333"
        assignment = dict(
            card=52, revision=7, role="one-dev", session_id=author,
            B="b" * 40, H="c" * 40,
        )
        registration = dict(session_id=author, phase="stable")
        registry = dict(roles=[
            dict(role="one-dev", id=author),
            dict(role="one-review", id=reviewer),
            dict(role="one-quality", id=quality),
        ])
        approval = dict(
            number=52, head="c" * 40, base="b" * 40, target="main",
            repo_id=LAB_REPO_ID, author="one-dev",
            required_reviews=["standards", "spec"],
            reviewers={
                "standards": "lab-role-one-review",
                "spec": "lab-role-one-quality",
            },
            review_ids={"standards": 101, "spec": 102},
        )
        return assignment, registration, registry, approval

    def refused(self, function, arguments):
        before = copy.deepcopy(arguments)
        with self.assertRaises(ValueError):
            function(*arguments)
        self.assertEqual(arguments, before, "INPUT_MUTATED_ON_REFUSAL")

    def test_01_current_owned_plan_returns_only_exact_intent(self):
        function = self.seam()
        arguments = self.fixture()
        before = copy.deepcopy(arguments)
        self.assertEqual(
            function(*arguments),
            dict(number=52, head="c" * 40, base="b" * 40, target="main"),
        )
        self.assertEqual(arguments, before, "INPUT_MUTATED")

    def test_02_stale_identity_or_wrong_scope_is_rejected(self):
        function = self.seam()
        for mutation in (
            "registration-id", "registration-phase", "author-role",
            "assignment-id", "head", "base", "target", "repository",
            "card", "missing-author", "duplicate-role",
        ):
            with self.subTest(mutation=mutation):
                arguments = self.fixture()
                assignment, registration, registry, approval = arguments
                if mutation == "registration-id":
                    registration["session_id"] = registry["roles"][1]["id"]
                elif mutation == "registration-phase":
                    registration["phase"] = "provisional"
                elif mutation == "author-role":
                    approval["author"] = "one-review"
                elif mutation == "assignment-id":
                    assignment["session_id"] = registry["roles"][1]["id"]
                elif mutation in ("head", "base"):
                    approval[mutation] = "d" * 40
                elif mutation == "target":
                    approval["target"] = "other"
                elif mutation == "repository":
                    approval["repo_id"] = LAB_REPO_ID + 1
                elif mutation == "card":
                    approval["number"] = 53
                elif mutation == "missing-author":
                    registry["roles"] = registry["roles"][1:]
                else:
                    registry["roles"].append(
                        copy.deepcopy(registry["roles"][0])
                    )
                self.refused(function, arguments)

    def test_03_two_independent_mapped_reviews_are_required(self):
        function = self.seam()
        for mutation in (
            "author-reviewer", "same-reviewer", "shared-native-id",
            "same-review-id", "missing-review-id", "boolean-review-id",
            "unmapped-reviewer", "missing-reviewer",
            "only-one-review", "duplicate-review-kind",
        ):
            with self.subTest(mutation=mutation):
                arguments = self.fixture()
                _, _, registry, approval = arguments
                if mutation == "author-reviewer":
                    approval["reviewers"]["standards"] = "lab-role-one-dev"
                elif mutation == "same-reviewer":
                    approval["reviewers"]["spec"] = (
                        approval["reviewers"]["standards"]
                    )
                elif mutation == "shared-native-id":
                    registry["roles"][2]["id"] = registry["roles"][1]["id"]
                elif mutation == "same-review-id":
                    approval["review_ids"]["spec"] = 101
                elif mutation == "missing-review-id":
                    del approval["review_ids"]["spec"]
                elif mutation == "boolean-review-id":
                    approval["review_ids"]["spec"] = True
                elif mutation == "unmapped-reviewer":
                    approval["reviewers"]["spec"] = "lab-role-one-unknown"
                elif mutation == "missing-reviewer":
                    del approval["reviewers"]["spec"]
                elif mutation == "only-one-review":
                    approval["required_reviews"] = ["standards"]
                else:
                    approval["required_reviews"] = ["standards", "standards"]
                self.refused(function, arguments)


if __name__ == "__main__":
    unittest.main(verbosity=2)
