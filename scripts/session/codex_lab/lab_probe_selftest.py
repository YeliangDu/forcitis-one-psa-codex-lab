"""Disposable Lab #9 proof. Never merge this fixture."""
import json
from pathlib import Path
import unittest
from lab_pr_ci import REPOSITORY, plan

class DisposableProbe(unittest.TestCase):
    def test_disposable_fixture_is_eligible_for_own_repo(self):
        fixture = json.loads(Path(__file__).with_name("lab_probe_fixture.json").read_text())
        head, base = "1" * 40, "2" * 40
        event = {"repository": {"full_name": fixture["repo"], "private": True},
                 "pull_request": {"head": {"repo": {"full_name": REPOSITORY}, "sha": head},
                                  "base": {"repo": {"full_name": REPOSITORY}, "sha": base, "ref": "main"}}}
        try:
            receipt = plan(event, head, ["scripts/session/codex_lab/lab_probe_selftest.py"])
        except ValueError as error:
            self.fail(f"Disposable fixture has no eligible plan: {error}")
        self.assertEqual(receipt["lanes"]["shadow"], "required")
        self.assertEqual(receipt["head_sha"], head)
