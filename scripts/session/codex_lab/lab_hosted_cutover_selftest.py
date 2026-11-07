"""Independent mandatory-secret schema transition; hosted execution only."""
import copy
import importlib.util
from pathlib import Path
import unittest

REPO = "YeliangDu/forcitis-one-psa-codex-lab"
BASE, HEAD = "a" * 40, "b" * 40


def load(name):
    path = Path(__file__).with_name(name + ".py")
    spec = importlib.util.spec_from_file_location("cutover_" + name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class LabHostedCutoverAcceptanceTest(unittest.TestCase):
    def event(self):
        return dict(repository=dict(full_name=REPO, private=True), pull_request=dict(
            head=dict(sha=HEAD, repo=dict(full_name=REPO)),
            base=dict(sha=BASE, ref="main", repo=dict(full_name=REPO))))

    def bundle(self):
        context = dict(repository=REPO, main_sha=BASE, queue_head=HEAD,
                       required_reviews=["首轮", "终审"])
        pr = dict(number=14, state="open", draft=False, merged=False, head_sha=HEAD,
                  base_sha=BASE, base_ref="main", head_repo=REPO, base_repo=REPO)
        run = dict(id=100, attempt=1, event="pull_request", path=".github/workflows/lab-pr-ci.yml",
                   repo=REPO, pr_number=14, head_sha=HEAD, status="completed",
                   conclusion="success", runner_group="GitHub-hosted")
        receipt = dict(head_sha=HEAD, observed_head=HEAD, base_sha=BASE, run_id=100,
                       run_attempt=1, ready=True, lanes={
                           lane: dict(applicability="required", proof="passed")
                           for lane in ("light", "shadow", "frontend", "backend", "secret")})
        reviews = [dict(kind=kind, verdict="pass", head_sha=HEAD) for kind in ("首轮", "终审")]
        return [context, pr, run, receipt, reviews]

    def decide(self, bundle):
        before = copy.deepcopy(bundle)
        result = load("lab_queue_release").plan_release(*bundle)
        self.assertEqual(before, bundle)
        self.assertTrue(result["reason"])
        return result

    def test_secret_always_required_including_docs_only_plans(self):
        cases = [(["docs/example.md"], {"light", "secret"}),
                 (["frontend/src/example.ts"], {"light", "secret", "frontend"}),
                 (["backend/pom.xml"], {"light", "secret", "backend"}),
                 (["scripts/session/codex_lab/example.py"], {"light", "secret", "shadow"}),
                 (["unknown/example.txt"], {"light", "secret", "shadow", "frontend", "backend"})]
        for paths, required in cases:
            with self.subTest(paths=paths):
                result = load("lab_pr_ci").plan(self.event(), HEAD, paths)
                self.assertEqual(HEAD, result["head_sha"])
                self.assertEqual(BASE, result["base_sha"])
                self.assertEqual({lane: "required" if lane in required else "not-applicable"
                                  for lane in ("light", "secret", "shadow", "frontend", "backend")},
                                 result["lanes"])

    def test_exact_five_lane_green_and_docs_only_receipts_can_release(self):
        for docs_only in (False, True):
            with self.subTest(docs_only=docs_only):
                bundle = self.bundle()
                if docs_only:
                    for lane in ("shadow", "frontend", "backend"):
                        bundle[3]["lanes"][lane] = dict(applicability="not-applicable", proof="not-applicable")
                result = self.decide(bundle)
                self.assertEqual("eligible", result["action"])
                self.assertTrue(result["release_key"])
                self.assertEqual(result["release_key"], self.decide(bundle)["release_key"])

    def test_old_four_lane_missing_skipped_or_failed_secret_holds(self):
        for secret in (None, {}, dict(applicability="not-applicable", proof="not-applicable"),
                       dict(applicability="required", proof="skipped"),
                       dict(applicability="required", proof="failed"),
                       dict(applicability="required", proof="missing")):
            with self.subTest(secret=secret):
                bundle = self.bundle()
                if secret is None:
                    del bundle[3]["lanes"]["secret"]
                else:
                    bundle[3]["lanes"]["secret"] = secret
                result = self.decide(bundle)
                self.assertEqual("hold", result["action"])
                self.assertFalse(result.get("release_key"))

    def test_secret_green_never_bypasses_existing_exact_head_requirements(self):
        for mutation in ("head", "base", "checkout", "queue", "run", "review", "light"):
            with self.subTest(mutation=mutation):
                bundle = self.bundle()
                if mutation == "head":
                    bundle[3]["head_sha"] = "c" * 40
                elif mutation == "base":
                    bundle[3]["base_sha"] = "c" * 40
                elif mutation == "checkout":
                    bundle[3]["observed_head"] = "c" * 40
                elif mutation == "queue":
                    bundle[0]["queue_head"] = None
                elif mutation == "run":
                    bundle[2]["conclusion"] = "failure"
                elif mutation == "review":
                    bundle[4] = []
                else:
                    bundle[3]["lanes"]["light"]["proof"] = "failed"
                self.assertEqual("hold", self.decide(bundle)["action"])


if __name__ == "__main__":
    unittest.main()
