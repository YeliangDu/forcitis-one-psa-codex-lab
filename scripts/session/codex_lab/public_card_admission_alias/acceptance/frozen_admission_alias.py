import copy
import importlib
import importlib.util
import unittest


SESSION = "11111111-1111-4111-8111-111111111111"
OTHER = "22222222-2222-4222-8222-222222222222"
ALIAS = "one-dev-readonly-lab"


class AdmissionAssignmentAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("card_plan") is not None:
            module = importlib.import_module("card_plan")
        function = getattr(module, "admission_assignment", None)
        self.assertTrue(
            callable(function), "ADMISSION_ASSIGNMENT_CAPABILITY_MISSING"
        )
        return function

    def fixture(self):
        return (
            dict(
                card=52, revision=7, role="one-readonly-lab",
                session_id=SESSION, B="b" * 40, H="c" * 40,
                metadata={"synthetic": True},
            ),
            dict(session_id=SESSION, phase="stable"),
            dict(roles=[dict(role=ALIAS, id=SESSION)]),
        )

    def test_01_only_admission_copy_receives_the_fixed_alias(self):
        function = self.seam()
        arguments = self.fixture()
        before = copy.deepcopy(arguments)
        result = function(*arguments)
        expected = copy.deepcopy(arguments[0])
        expected["role"] = ALIAS
        self.assertEqual(result, expected)
        self.assertEqual(arguments, before)
        self.assertIsNot(result, arguments[0])
        result["metadata"]["synthetic"] = False
        self.assertEqual(arguments, before, "COPY_SHARES_MUTABLE_STATE")

    def test_02_unstable_ambiguous_or_other_mapping_is_rejected(self):
        function = self.seam()
        cases = [
            ("phase", "provisional"),
            ("phase", "retired"),
            ("registration-id", OTHER),
            ("alias-id", OTHER),
            ("mapping", "one-dev-other"),
            ("missing", None),
            ("duplicate-role", None),
            ("duplicate-id", None),
        ]
        cases.extend(
            ("mapping", role) for role in (
                "one-lead", "one-design", "one-platform",
                "one-test", "one-review", "one-quality",
            )
        )
        for mutation, value in cases:
            with self.subTest(mutation=mutation, value=value):
                arguments = self.fixture()
                _, registration, registry = arguments
                if mutation == "phase":
                    registration["phase"] = value
                elif mutation == "registration-id":
                    registration["session_id"] = value
                elif mutation == "alias-id":
                    registry["roles"][0]["id"] = value
                elif mutation == "mapping":
                    registry["roles"][0]["role"] = value
                elif mutation == "missing":
                    registry["roles"] = []
                elif mutation == "duplicate-role":
                    registry["roles"].append(dict(role=ALIAS, id=OTHER))
                else:
                    registry["roles"].append(
                        dict(role="one-dev-other", id=SESSION)
                    )
                before = copy.deepcopy(arguments)
                with self.assertRaises(ValueError):
                    function(*arguments)
                self.assertEqual(arguments, before)

    def test_03_normal_roles_are_not_remapped(self):
        function = self.seam()
        for role in (
            "one-lead", "one-design", "one-platform",
            "one-test", "one-dev-example",
        ):
            with self.subTest(role=role):
                assignment, registration, _ = self.fixture()
                assignment["role"] = role
                registry = dict(roles=[dict(role=role, id=SESSION)])
                arguments = (assignment, registration, registry)
                before = copy.deepcopy(arguments)
                result = function(*arguments)
                self.assertEqual(result, assignment)
                self.assertIsNot(result, assignment)
                result["metadata"]["synthetic"] = False
                self.assertEqual(arguments, before)


if __name__ == "__main__":
    unittest.main(verbosity=2)
