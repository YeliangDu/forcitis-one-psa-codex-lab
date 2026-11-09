import copy
import hashlib
import importlib
import importlib.util
import json
import unittest


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def encode(value):
    return json.dumps(
        value, sort_keys=True, separators=(",", ":"), ensure_ascii=False
    ).encode("utf-8")


class CardCapsuleAcceptance(unittest.TestCase):
    def seam(self, name):
        module = None
        if importlib.util.find_spec("card_capsule") is not None:
            module = importlib.import_module("card_capsule")
        function = getattr(module, name, None)
        self.assertTrue(
            callable(function), "CARD_CAPSULE_CAPABILITY_MISSING:" + name
        )
        return function

    def fixture(self):
        assignment = dict(
            card=52, revision=3,
            session_id="11111111-1111-4111-8111-111111111111",
            B="b" * 40,
        )
        scope = dict(assignment, files=[
            dict(
                path="fixture/logic.py",
                sha256=sha(b"value = 1\n"),
                text="value = 1\n", writable=True,
            ),
            dict(
                path="fixture/card.txt",
                sha256=sha("封存说明\n".encode("utf-8")),
                text="封存说明\n", writable=False,
            ),
        ])
        raw = encode(scope)
        approved = sha(raw)
        patch = dict(
            revision=3, scope_sha256=approved,
            edits=[dict(
                path="fixture/logic.py",
                before_sha256=sha(b"value = 1\n"),
                old="value = 1", new="value = 2",
            )],
        )
        return assignment, scope, raw, approved, patch

    def call_unchanged(self, function, *arguments):
        before = copy.deepcopy(arguments)
        result = function(*arguments)
        self.assertEqual(arguments, before, "INPUT_MUTATED")
        return result

    def refuse(self, function, *arguments):
        before = copy.deepcopy(arguments)
        with self.assertRaises(ValueError):
            function(*arguments)
        self.assertEqual(arguments, before, "INPUT_MUTATED_ON_REFUSAL")

    def test_01_read_returns_only_sealed_text(self):
        function = self.seam("read_scope")
        assignment, scope, raw, approved, _ = self.fixture()
        self.assertEqual(
            self.call_unchanged(function, assignment, raw, approved),
            {row["path"]: row["text"] for row in scope["files"]},
        )

    def test_02_prepare_returns_exact_modified_content(self):
        function = self.seam("prepare_edits")
        assignment, _, raw, approved, patch = self.fixture()
        self.assertEqual(
            self.call_unchanged(
                function, assignment, raw, approved, patch
            ),
            {"fixture/logic.py": "value = 2\n"},
        )

    def test_03_read_rejects_stale_identity_digest_and_unsafe_scope(self):
        function = self.seam("read_scope")
        for key, replacement in (
            ("card", 53), ("revision", 2),
            ("session_id", "22222222-2222-4222-8222-222222222222"),
            ("B", "c" * 40),
        ):
            with self.subTest(stale=key):
                assignment, _, raw, approved, _ = self.fixture()
                assignment[key] = replacement
                self.refuse(function, assignment, raw, approved)

        assignment, _, raw, _, _ = self.fixture()
        self.refuse(function, assignment, raw, "0" * 64)
        self.refuse(function, assignment, b"not-json", sha(b"not-json"))

        for mutation in (
            "absolute", "parent", "embedded-parent", "backslash",
            "duplicate", "preimage", "text",
        ):
            with self.subTest(scope=mutation):
                assignment, scope, _, _, _ = self.fixture()
                row = scope["files"][0]
                if mutation == "duplicate":
                    scope["files"].append(copy.deepcopy(row))
                elif mutation == "preimage":
                    row["sha256"] = "0" * 64
                elif mutation == "text":
                    row["text"] = "different content\n"
                else:
                    row["path"] = {
                        "absolute": "/fixture/logic.py",
                        "parent": "../logic.py",
                        "embedded-parent": "fixture/../logic.py",
                        "backslash": "fixture\\logic.py",
                    }[mutation]
                raw = encode(scope)
                self.refuse(function, assignment, raw, sha(raw))

    def test_04_prepare_rejects_unauthorized_or_ambiguous_edits(self):
        function = self.seam("prepare_edits")
        mutations = (
            "assignment", "approved-digest", "patch-revision",
            "patch-digest", "outside", "absolute", "parent",
            "embedded-parent", "backslash", "readonly",
            "duplicate", "preimage", "missing-old", "ambiguous-old",
            "empty-old", "unchanged", "no-edits", "nonboolean-writable",
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                assignment, scope, raw, approved, patch = self.fixture()
                edit = patch["edits"][0]
                if mutation == "assignment":
                    assignment["revision"] = 2
                elif mutation == "approved-digest":
                    approved = "0" * 64
                elif mutation == "patch-revision":
                    patch["revision"] = 2
                elif mutation == "patch-digest":
                    patch["scope_sha256"] = "0" * 64
                elif mutation in (
                    "outside", "absolute", "parent",
                    "embedded-parent", "backslash",
                ):
                    edit["path"] = {
                        "outside": "outside.py",
                        "absolute": "/fixture/logic.py",
                        "parent": "../logic.py",
                        "embedded-parent": "fixture/../logic.py",
                        "backslash": "fixture\\logic.py",
                    }[mutation]
                elif mutation == "readonly":
                    edit.update(
                        path="fixture/card.txt",
                        before_sha256=scope["files"][1]["sha256"],
                        old="封存说明", new="changed",
                    )
                elif mutation == "duplicate":
                    patch["edits"].append(copy.deepcopy(edit))
                elif mutation == "preimage":
                    edit["before_sha256"] = "0" * 64
                elif mutation == "missing-old":
                    edit["old"] = "absent"
                elif mutation == "empty-old":
                    edit["old"] = ""
                elif mutation == "unchanged":
                    edit["new"] = edit["old"]
                elif mutation == "no-edits":
                    patch["edits"] = []
                elif mutation == "ambiguous-old":
                    scope["files"][0]["text"] = "value = 1\nvalue = 1\n"
                    scope["files"][0]["sha256"] = sha(
                        scope["files"][0]["text"].encode("utf-8")
                    )
                    edit["before_sha256"] = scope["files"][0]["sha256"]
                else:
                    scope["files"][0]["writable"] = "true"

                if mutation in ("ambiguous-old", "nonboolean-writable"):
                    raw = encode(scope)
                    approved = sha(raw)
                    patch["scope_sha256"] = approved

                self.refuse(
                    function, assignment, raw, approved, patch
                )


if __name__ == "__main__":
    unittest.main(verbosity=2)
