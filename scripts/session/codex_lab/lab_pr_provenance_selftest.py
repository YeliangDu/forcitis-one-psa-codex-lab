"""Independent regression for ProductionPorts._provenance.

Author receipt:
  role: one-test
  native_thread_id: 01a1b614-934c-7250-a715-5cf09e9c02c2
  native_turn_id: unavailable; not fabricated
  baseline: 77e872e8ba9661d3294d3a9337c8e5c956b70641
  execution: not run by author

Expected baseline: 13 tests, 4 assertion failures, 0 errors.
Only actual _provenance is called; ProductionPorts.__init__ is bypassed.
"""
import importlib
import json
from pathlib import Path
import sys
import tempfile
import unittest


AUTHOR = "11111111-1111-4111-8111-111111111111"
ACCEPTANCE = "22222222-2222-4222-8222-222222222222"
REVIEWER = "33333333-3333-4333-8333-333333333333"
WRONG = "44444444-4444-4444-8444-444444444444"
AUTHOR_LINE = f"Codex: {AUTHOR}\n"
ACCEPTANCE_LINE = f"Codex-Acceptance: {ACCEPTANCE}\n"
ACCEPTANCE_PATH = (
    "backend/src/test/java/com/forcitis/one/acceptance/"
    "SyntheticAcceptanceTest.java"
)
ORDINARY_PATH = "scripts/session/codex_lab/synthetic_fixture.py"


def target():
    root = Path(__file__).resolve().parents[3]
    merge = root / "scripts" / "merge"
    location = str(merge)
    if location not in sys.path:
        sys.path.insert(0, location)
    return importlib.import_module("mq_lab_operator").ProductionPorts


class LabPrProvenanceAcceptance(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="lab-pr-provenance-")
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name).resolve(strict=True)
        registry = root / ".codex-lab/session-copy/runtime/roles.json"
        registry.parent.mkdir(parents=True)
        registry.write_text(json.dumps({
            "roles": [
                {"role": "one-dev", "id": AUTHOR},
                {"role": "one-test", "id": ACCEPTANCE},
                {"role": "one-review", "id": REVIEWER},
            ],
        }))
        registry.chmod(0o600)

        production = target()
        self.ports = production.__new__(production)
        self.ports.directory = root / "synthetic-git" / "merge-queue"
        self.ports.directory.mkdir(parents=True)
        self.approval = {
            "author": "one-dev",
            "reviewers": {"standards": "lab-role-one-review"},
        }

    def check(self, body, acceptance=False, previous=None):
        row = {
            "filename": ACCEPTANCE_PATH if acceptance else ORDINARY_PATH,
            "status": "modified",
        }
        if previous is not None:
            row.update(status="renamed", previous_filename=previous)
        return self.ports._provenance(
            {"body": body}, self.approval, [row]
        )

    def rejected(self, body, reason, **scope):
        with self.assertRaisesRegex(ValueError, "^" + reason + "$"):
            self.check(body, **scope)

    def test_01_real_author_body_passes_for_ordinary_file(self):
        self.check("Synthetic change.\n\n" + AUTHOR_LINE)

    def test_02_real_author_and_independent_acceptance_pass(self):
        self.check(
            AUTHOR_LINE + ACCEPTANCE_LINE, acceptance=True
        )

    def test_03_wrong_author_session_is_rejected(self):
        self.rejected(
            f"Codex: {WRONG}\n",
            "missing-current-Codex-provenance",
        )

    def test_04_backtick_fenced_author_is_not_provenance(self):
        self.rejected(
            "```text\n" + AUTHOR_LINE + "```\n",
            "missing-current-Codex-provenance",
        )

    def test_05_tilde_fenced_author_is_not_provenance(self):
        self.rejected(
            "~~~text\n" + AUTHOR_LINE + "~~~\n",
            "missing-current-Codex-provenance",
        )

    def test_06_acceptance_file_needs_separate_acceptance_line(self):
        self.rejected(
            AUTHOR_LINE,
            "missing-Codex-acceptance-provenance",
            acceptance=True,
        )

    def test_07_author_session_cannot_sign_as_acceptance(self):
        self.rejected(
            AUTHOR_LINE + f"Codex-Acceptance: {AUTHOR}\n",
            "missing-Codex-acceptance-provenance",
            acceptance=True,
        )

    def test_08_backtick_fenced_acceptance_is_not_provenance(self):
        self.rejected(
            AUTHOR_LINE + "\n```text\n" + ACCEPTANCE_LINE + "```\n",
            "missing-Codex-acceptance-provenance",
            acceptance=True,
        )

    def test_09_tilde_fenced_acceptance_is_not_provenance(self):
        self.rejected(
            AUTHOR_LINE + "\n~~~text\n" + ACCEPTANCE_LINE + "~~~\n",
            "missing-Codex-acceptance-provenance",
            acceptance=True,
        )

    def test_10_renaming_out_of_acceptance_keeps_acceptance_requirement(self):
        self.rejected(
            AUTHOR_LINE,
            "missing-Codex-acceptance-provenance",
            previous=ACCEPTANCE_PATH,
        )
        self.check(
            AUTHOR_LINE + ACCEPTANCE_LINE,
            previous=ACCEPTANCE_PATH,
        )

    def test_11_single_line_quotes_are_not_provenance(self):
        for prefix in ("> ", "&gt; "):
            with self.subTest(prefix=prefix):
                self.rejected(
                    prefix + AUTHOR_LINE,
                    "missing-current-Codex-provenance",
                )
                self.rejected(
                    AUTHOR_LINE + prefix + ACCEPTANCE_LINE,
                    "missing-Codex-acceptance-provenance",
                    acceptance=True,
                )

    def test_12_real_lines_after_fences_still_pass(self):
        for fence in ("```", "~~~"):
            with self.subTest(fence=fence):
                self.check(
                    fence + "text\n"
                    + f"Codex: {WRONG}\n"
                    + f"Codex-Acceptance: {WRONG}\n"
                    + fence + "\n\n"
                    + AUTHOR_LINE + ACCEPTANCE_LINE,
                    acceptance=True,
                )

    def test_13_acceptance_signature_cannot_replace_author_signature(self):
        self.rejected(
            ACCEPTANCE_LINE,
            "missing-current-Codex-provenance",
            acceptance=True,
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
