"""Lab #14 pure release-plan acceptance; execute on GitHub-hosted CI only."""

import copy
import importlib.util
from pathlib import Path
import unittest


REPO = "YeliangDu/forcitis-one-psa-codex-lab"
BASE = "a" * 40
HEAD = "b" * 40
OTHER = "c" * 40


def planner():
    source = Path(__file__).with_name("lab_queue_release.py")
    if not source.exists():
        return lambda *args: {"action": "hold", "reason": "manual-no-exact-proof"}
    spec = importlib.util.spec_from_file_location("lab_queue_release_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.plan_release


class LabQueueReleaseAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.context = dict(repository=REPO, main_sha=BASE, queue_head=HEAD,
                            required_reviews=["首轮", "终审"])
        self.pr = dict(number=14, state="open", draft=False, head_sha=HEAD,
                       base_sha=BASE, base_ref="main", head_repo=REPO,
                       base_repo=REPO, merged=False)
        self.run = dict(id=100, attempt=1, event="pull_request",
                        path=".github/workflows/lab-pr-ci.yml", repo=REPO,
                        pr_number=14, head_sha=HEAD, status="completed",
                        conclusion="success", runner_group="GitHub-hosted")
        self.receipt = dict(head_sha=HEAD, observed_head=HEAD, base_sha=BASE,
                            run_id=100, run_attempt=1, ready=True,
                            lanes={lane: dict(applicability="required", proof="passed")
                                   for lane in ("light", "shadow", "frontend", "backend")})
        self.reviews = [dict(kind=kind, verdict="pass", head_sha=HEAD)
                        for kind in ("首轮", "终审")]

    def bundle(self):
        return copy.deepcopy([self.context, self.pr, self.run, self.receipt, self.reviews])

    def decide(self, bundle):
        before = copy.deepcopy(bundle)
        result = planner()(*bundle)
        self.assertEqual(before, bundle, "pure planner must not mutate evidence")
        self.assertIsInstance(result, dict)
        self.assertIsInstance(result["reason"], str)
        self.assertTrue(result["reason"].strip())
        return result

    def test_valid_exact_head_has_stable_pr_head_base_release_key(self):
        result = self.decide(self.bundle())
        self.assertEqual("eligible", result["action"])
        self.assertTrue(result["release_key"])
        self.assertEqual(result["release_key"], self.decide(self.bundle())["release_key"])
        for field in ("number", "head_sha", "base_sha"):
            with self.subTest(identity=field):
                bundle = self.bundle()
                if field == "number":
                    bundle[1][field] = bundle[2]["pr_number"] = 15
                elif field == "head_sha":
                    bundle[0]["queue_head"] = OTHER
                    bundle[1][field] = bundle[2][field] = OTHER
                    bundle[3]["head_sha"] = bundle[3]["observed_head"] = OTHER
                    for review in bundle[4]:
                        review["head_sha"] = OTHER
                else:
                    bundle[0]["main_sha"] = bundle[1][field] = bundle[3][field] = OTHER
                changed = self.decide(bundle)
                self.assertEqual("eligible", changed["action"])
                self.assertNotEqual(result["release_key"], changed["release_key"])

    def test_wrong_repo_draft_stale_or_missing_admission_and_shas_hold(self):
        cases = [(0, "repository", "synthetic/foreign"), (1, "head_repo", "synthetic/fork"),
                 (1, "base_repo", "synthetic/foreign"), (1, "draft", True),
                 (1, "state", "closed"), (1, "base_ref", "topic"),
                 (0, "queue_head", None), (0, "queue_head", OTHER),
                 (0, "main_sha", OTHER), (1, "head_sha", OTHER),
                 (1, "base_sha", OTHER), (1, "head_sha", "bad-sha")]
        for index, field, value in cases:
            with self.subTest(field=field, value=value):
                bundle = self.bundle()
                bundle[index][field] = value
                result = self.decide(bundle)
                self.assertEqual("hold", result["action"])
                self.assertFalse(result.get("release_key"))
        for index, field in ((0, "queue_head"), (0, "main_sha"),
                             (1, "head_sha"), (1, "base_sha")):
            with self.subTest(missing=field):
                bundle = self.bundle()
                del bundle[index][field]
                self.assertEqual("hold", self.decide(bundle)["action"])

    def test_missing_failed_or_foreign_run_receipt_and_lanes_hold(self):
        cases = [(2, "repo", "synthetic/foreign"), (2, "pr_number", 15),
                 (2, "head_sha", OTHER), (2, "event", "workflow_dispatch"),
                 (2, "path", ".github/workflows/other.yml"),
                 (2, "status", "in_progress"), (2, "conclusion", "failure"),
                 (2, "runner_group", "self-hosted"), (3, "run_id", 101),
                 (3, "run_attempt", 2), (3, "observed_head", OTHER),
                 (3, "head_sha", OTHER), (3, "base_sha", OTHER),
                 (3, "ready", False), (3, "ready", "true")]
        for index, field, value in cases:
            with self.subTest(field=field, value=value):
                bundle = self.bundle()
                bundle[index][field] = value
                self.assertEqual("hold", self.decide(bundle)["action"])
        for index in (2, 3):
            with self.subTest(missing_bundle=index):
                bundle = self.bundle()
                bundle[index] = None
                self.assertEqual("hold", self.decide(bundle)["action"])
        for lane in self.receipt["lanes"]:
            for mutation in (None, dict(applicability="required", proof="failed"),
                             dict(applicability="required", proof="missing")):
                with self.subTest(lane=lane, mutation=mutation):
                    bundle = self.bundle()
                    if mutation is None:
                        del bundle[3]["lanes"][lane]
                    else:
                        bundle[3]["lanes"][lane] = mutation
                    self.assertEqual("hold", self.decide(bundle)["action"])

    def test_latest_required_current_head_reviews_decide(self):
        for reviews in ([], self.reviews[:1],
                        [dict(kind="首轮", verdict="pass", head_sha=OTHER), self.reviews[1]],
                        self.reviews + [dict(kind="终审", verdict="fail", head_sha=HEAD)]):
            with self.subTest(reviews=reviews):
                bundle = self.bundle()
                bundle[4] = copy.deepcopy(reviews)
                self.assertEqual("hold", self.decide(bundle)["action"])
        bundle = self.bundle()
        bundle[4] = [dict(kind="终审", verdict="fail", head_sha=HEAD)] + bundle[4]
        self.assertEqual("eligible", self.decide(bundle)["action"])

    def test_already_merged_skips_even_without_release_proof(self):
        bundle = self.bundle()
        bundle[1].update(merged=True, state="closed")
        bundle[2] = bundle[3] = None
        bundle[4] = []
        self.assertEqual("skip", self.decide(bundle)["action"])


if __name__ == "__main__":
    unittest.main()
