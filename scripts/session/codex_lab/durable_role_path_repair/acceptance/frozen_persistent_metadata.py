"""Independent persistent-slot metadata policy acceptance.

Frozen contract:
  role_native.policy_additions(root: pathlib.Path) -> Seatbelt policy text.

Only proper ancestors of the canonical slot may receive exterior read access:
  (allow file-read-metadata (literal "<ancestor>"))
No ancestor file-content or subtree access is permitted.
Owner/credentials subtree read denials remain present.

Synthetic paths only; no filesystem, native process, or policy execution.
Existing durable_role tests remain unchanged. Author has not run this suite.

Three test methods. The old missing-ancestor behavior must fail with:
  PERSISTENT_ANCESTOR_METADATA_MISSING
The exact total failure count depends on existing invalid-input handling.
"""
import importlib
import json
from pathlib import Path
import re
import unittest


ROOT = Path(
    "/Users/synthetic/PersonalLab/.codex-lab/"
    "role-runtime/slots/one-readonly-lab"
)


def expressions(text):
    """Parse policy structure without executing Seatbelt or accepting comments."""
    if isinstance(text, bytes):
        text = text.decode("utf-8", errors="strict")
    if not isinstance(text, str):
        raise AssertionError("POLICY_TEXT_REQUIRED")
    token_pattern = re.compile(
        r'\s+|;[^\n]*|[()]|"(?:\\.|[^"\\])*"|[^\s();"]+'
    )
    tokens = []
    position = 0
    for match in token_pattern.finditer(text):
        if match.start() != position:
            raise AssertionError("UNPARSEABLE_POLICY")
        position = match.end()
        token = match.group()
        if token.isspace() or token.startswith(";"):
            continue
        tokens.append(token)
    if position != len(text):
        raise AssertionError("UNPARSEABLE_POLICY")

    stack = [[]]
    for token in tokens:
        if token == "(":
            value = []
            stack[-1].append(value)
            stack.append(value)
        elif token == ")":
            if len(stack) == 1:
                raise AssertionError("UNBALANCED_POLICY")
            stack.pop()
        elif token.startswith('"'):
            stack[-1].append(json.loads(token))
        else:
            stack[-1].append(token)
    if len(stack) != 1:
        raise AssertionError("UNBALANCED_POLICY")
    return stack[0]


def selectors(value):
    found = []
    if isinstance(value, list):
        if value and value[0] in ("literal", "subpath", "regex"):
            found.append(value)
        for child in value:
            found.extend(selectors(child))
    return found


def inside(path, root):
    return path == str(root) or path.startswith(str(root) + "/")


class PersistentMetadataPolicyAcceptance(unittest.TestCase):
    def seam(self):
        module = importlib.import_module("role_native")
        function = getattr(module, "policy_additions", None)
        self.assertTrue(
            callable(function), "POLICY_ADDITIONS_CAPABILITY_MISSING"
        )
        return function

    def test_01_every_proper_ancestor_has_only_literal_metadata_grant(self):
        function = self.seam()
        rules = expressions(function(ROOT))
        expected = {str(parent) for parent in ROOT.parents}
        granted = set()
        for rule in rules:
            if (
                isinstance(rule, list) and len(rule) >= 3
                and rule[0] == "allow"
                and "file-read-metadata" in rule[1:]
            ):
                for selector in selectors(rule):
                    if len(selector) == 2 and selector[0] == "literal":
                        granted.add(selector[1])
        self.assertTrue(
            expected <= granted,
            "PERSISTENT_ANCESTOR_METADATA_MISSING:"
            + repr(sorted(expected - granted)),
        )

    def test_02_no_exterior_content_or_subtree_grants_and_denials_remain(self):
        function = self.seam()
        rules = expressions(function(ROOT))
        ancestors = {str(parent) for parent in ROOT.parents}
        denied = set()

        for rule in rules:
            if not isinstance(rule, list) or len(rule) < 2:
                continue
            operations = [
                value for value in rule[1:] if isinstance(value, str)
            ]
            paths = selectors(rule)
            if rule[0] == "deny" and "file-read*" in operations:
                denied.update(
                    selector[1] for selector in paths
                    if len(selector) == 2 and selector[0] == "subpath"
                )
            if rule[0] != "allow":
                continue
            reads = [
                operation for operation in operations
                if operation.startswith("file-read")
            ]
            if not reads:
                continue
            self.assertTrue(paths, "UNBOUNDED_FILE_READ_GRANT")
            for selector in paths:
                self.assertEqual(len(selector), 2)
                kind, path = selector
                self.assertIn(kind, ("literal", "subpath"),
                              "REGEX_READ_GRANT_FORBIDDEN")
                self.assertIsInstance(path, str)
                if inside(path, ROOT):
                    self.assertNotIn(
                        ".git", Path(path).parts, "GIT_READ_GRANT_FORBIDDEN"
                    )
                    continue
                self.assertEqual(kind, "literal",
                                 "ANCESTOR_SUBTREE_GRANT_FORBIDDEN")
                self.assertIn(path, ancestors,
                              "UNRELATED_READ_GRANT_FORBIDDEN")
                self.assertEqual(
                    reads, ["file-read-metadata"],
                    "ANCESTOR_CONTENT_GRANT_FORBIDDEN",
                )

        self.assertIn(str(ROOT / "owner"), denied,
                      "OWNER_READ_DENY_REMOVED")
        self.assertIn(str(ROOT / "credentials"), denied,
                      "CREDENTIALS_READ_DENY_REMOVED")

    def test_03_noncanonical_roots_and_policy_injection_are_rejected(self):
        function = self.seam()
        invalid = (
            Path("relative/.codex-lab/role-runtime/slots/one-readonly-lab"),
            Path("/Users/synthetic/PersonalLab/tmp/one-readonly-lab"),
            ROOT.parent,
            ROOT / ".." / "other",
            ROOT.parent / 'bad")(allow file-read* (subpath "/"))',
            ROOT.parent / "bad\nrole",
        )
        for root in invalid:
            with self.subTest(root=str(root)):
                with self.assertRaises(ValueError):
                    function(root)


if __name__ == "__main__":
    unittest.main(verbosity=2)
