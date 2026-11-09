"""Lab #9 plan acceptance only; workflow activation needs hosted PR evidence."""

import copy
import importlib.util
from pathlib import Path
import unittest


REPO = "YeliangDu/forcitis-one-psa-codex-lab"
REPO_ID = 1449415666
BASE = "a8454ced28ab18fce37d495f21d0daae2fa6ce8d"
HEAD = "1" * 40


def planner():
    source = Path(__file__).with_name("lab_pr_ci.py")
    if not source.exists():
        # The manual baseline supplies no exact-head plan. Fail in assertions.
        return lambda event, observed_head, paths: None
    spec = importlib.util.spec_from_file_location("lab_pr_ci_acceptance_target", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.plan


class LabPrCiAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.event = {
            "repository": {"full_name": REPO, "id": REPO_ID, "private": False},
            "pull_request": {
                "head": {"repo": {"full_name": REPO, "id": REPO_ID, "private": False}, "sha": HEAD},
                "base": {"repo": {"full_name": REPO, "id": REPO_ID, "private": False}, "ref": "main", "sha": BASE},
            },
        }

    def assert_plan(self, paths, required):
        event = copy.deepcopy(self.event)
        original_paths = list(paths)
        result = planner()(event, HEAD, paths)
        self.assertIsInstance(result, dict, "accepted paths need an exact-head receipt")
        self.assertEqual(HEAD, result["head_sha"])
        self.assertEqual(BASE, result["base_sha"])
        self.assertEqual(
            {lane: "required" if lane in required else "not-applicable"
             for lane in ("light", "secret", "shadow", "frontend", "backend")},
            result["lanes"],
        )
        self.assertEqual(self.event, event)
        self.assertEqual(original_paths, paths)

    def test_same_repo_exact_head_receipt_and_path_lanes(self):
        cases = [
            (["scripts/session/codex_lab/shadow.py"], {"light", "secret", "shadow"}),
            ([".github/workflows/lab-pr-ci.yml"], {"light", "secret", "shadow"}),
            (["frontend/src/example.ts"], {"light", "secret", "frontend"}),
            (["docs/glossary/bcs.yaml"], {"light", "secret", "frontend"}),
            (["backend/src/main/java/Synthetic.java"], {"light", "secret", "backend"}),
            (["backend/pom.xml", "frontend/package.json",
              "scripts/session/codex_lab/shadow.py"],
             {"light", "secret", "shadow", "frontend", "backend"}),
        ]
        for paths, required in cases:
            with self.subTest(paths=paths):
                self.assert_plan(paths, required)

    def test_reject_wrong_repo_fork_base_checkout_and_unsafe_paths(self):
        events = []
        for field in ("repository", "head", "base"):
            event = copy.deepcopy(self.event)
            target = event["repository"] if field == "repository" else event["pull_request"][field]["repo"]
            target["full_name"] = "synthetic/forbidden"
            events.append(event)
        event = copy.deepcopy(self.event)
        event["pull_request"]["base"]["ref"] = "topic"
        events.append(event)
        for field in ("base", "head"):
            for invalid in (None, "", "not-a-sha", "a" * 39, "g" * 40):
                event = copy.deepcopy(self.event)
                event["pull_request"][field]["sha"] = invalid
                events.append(event)
        for index, event in enumerate(events):
            with self.subTest(event=index):
                with self.assertRaises(ValueError):
                    planner()(event, HEAD, ["docs/example.md"])
        for observed in (BASE, None, "invalid"):
            with self.subTest(observed=observed):
                with self.assertRaises(ValueError):
                    planner()(copy.deepcopy(self.event), observed, ["docs/example.md"])
        for paths in ([], [""], ["/etc/passwd"], ["../outside"],
                      ["docs/../../outside"], ["docs/../backend/pom.xml"],
                      ["docs/example\n.md"], ["docs/example\x00.md"],
                      ["docs\\..\\outside"], ["docs/example.md", "../outside"]):
            with self.subTest(paths=paths):
                with self.assertRaises(ValueError):
                    planner()(copy.deepcopy(self.event), HEAD, paths)

    def test_pure_docs_and_unknown_paths_cannot_silently_skip(self):
        for paths, required in (
            (["docs/example.md"], {"light", "secret"}),
            (["unclassified/synthetic.txt"], {"light", "secret", "shadow", "frontend", "backend"}),
            (["docs/example.md", "unclassified/synthetic.txt"],
             {"light", "secret", "shadow", "frontend", "backend"}),
        ):
            with self.subTest(paths=paths):
                self.assert_plan(paths, required)


if __name__ == "__main__":
    unittest.main()
