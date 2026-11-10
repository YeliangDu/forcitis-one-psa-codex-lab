"""Independent AdoptionPort provenance integration acceptance.

Calls actual AdoptionPort.observe_remote, bypassing __init__.
Only repo/api/pull/ref are replaced with synthetic read-only ports.
Each PR body has its own matching configured SHA-256, so fence rejection
cannot be attributed to a digest mismatch.

Existing adoption/preserved-update suites remain unchanged.
Author has not executed this suite or operated an actual PR.

Current splitlines candidate expected:
  3 tests, 4 assertion failures, 0 errors, exit 1.
  Failures: two fence forms for author and acceptance provenance.
"""
import copy
import hashlib
import importlib
import unittest


AUTHOR = "11111111-1111-4111-8111-111111111111"
ACCEPTANCE = "22222222-2222-4222-8222-222222222222"
AUTHOR_LINE = f"Codex: {AUTHOR}\n"
ACCEPTANCE_LINE = f"Codex-Acceptance: {ACCEPTANCE}\n"


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


class AdoptionPortProvenanceAcceptance(unittest.TestCase):
    def fixture(self, body):
        module = importlib.import_module("existing_pr_adoption")
        port = module.AdoptionPort.__new__(module.AdoptionPort)
        scope = dict(
            repo_id=1449415666,
            repository="YeliangDu/forcitis-one-psa-codex-lab",
            card=67,
            number=68,
            head_ref="codex/preserved-card67",
            base_ref="main",
            old_head="a" * 40,
            old_base="b" * 40,
            new_base="c" * 40,
            scope_sha256="d" * 64,
            author_session=AUTHOR,
            acceptance_session=ACCEPTANCE,
            acceptance_sha256="e" * 64,
        )
        issue_body = "Synthetic signed card scope.\n"
        config = dict(
            issue_body_sha256=sha(issue_body),
            pr_body_sha256=sha(body),
        )
        row = dict(
            number=68,
            head=dict(ref=scope["head_ref"], sha=scope["old_head"]),
            base=dict(ref="main", sha=scope["old_base"]),
            state="open",
            draft=False,
            body=body,
        )
        calls = []

        def repo():
            calls.append(("repo",))
            return dict(
                id=scope["repo_id"],
                full_name=scope["repository"],
                default_branch="main",
            )

        def api(path):
            calls.append(("api", path))
            self.assertEqual(path, "/issues/67")
            return dict(number=67, state="open", body=issue_body)

        def pull(number):
            calls.append(("pull", number))
            self.assertEqual(number, 68)
            return copy.deepcopy(row)

        def ref(branch):
            calls.append(("ref", branch))
            self.assertEqual(branch, scope["head_ref"])
            return scope["old_head"]

        port.repo = repo
        port.api = api
        port.pull = pull
        port.ref = ref
        return port, scope, config, row, calls

    def assert_reads(self, calls, scope):
        self.assertEqual(calls, [
            ("repo",),
            ("api", "/issues/67"),
            ("pull", 68),
            ("ref", scope["head_ref"]),
        ])

    def test_01_actual_body_provenance_passes(self):
        body = "Refs #67\n" + AUTHOR_LINE + ACCEPTANCE_LINE
        port, scope, config, row, calls = self.fixture(body)
        before = copy.deepcopy((scope, config))
        self.assertEqual(port.observe_remote(scope, config), row)
        self.assertEqual((scope, config), before)
        self.assert_reads(calls, scope)

    def test_02_fenced_author_is_rejected_despite_matching_body_digest(self):
        for fence in ("```", "~~~"):
            with self.subTest(fence=fence):
                body = (
                    "Refs #67\n"
                    + fence + "text\n" + AUTHOR_LINE + fence + "\n"
                    + ACCEPTANCE_LINE
                )
                port, scope, config, _, calls = self.fixture(body)
                self.assertEqual(config["pr_body_sha256"], sha(body))
                with self.assertRaisesRegex(
                    ValueError,
                    "^existing-pr-original-provenance-unbound$",
                ):
                    port.observe_remote(scope, config)
                self.assert_reads(calls, scope)

    def test_03_fenced_acceptance_is_rejected_despite_matching_body_digest(self):
        for fence in ("```", "~~~"):
            with self.subTest(fence=fence):
                body = (
                    "Refs #67\n" + AUTHOR_LINE
                    + fence + "text\n" + ACCEPTANCE_LINE + fence + "\n"
                )
                port, scope, config, _, calls = self.fixture(body)
                self.assertEqual(config["pr_body_sha256"], sha(body))
                with self.assertRaisesRegex(
                    ValueError,
                    "^existing-pr-original-provenance-unbound$",
                ):
                    port.observe_remote(scope, config)
                self.assert_reads(calls, scope)


if __name__ == "__main__":
    unittest.main(verbosity=2)
