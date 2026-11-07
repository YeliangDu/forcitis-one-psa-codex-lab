"""Independent synthetic GitHub evidence tests; hosted execution only."""
import copy
import importlib.util
from pathlib import Path
import unittest

REPO = "YeliangDu/forcitis-one-psa-codex-lab"
BASE, HEAD, OLD = "a" * 40, "b" * 40, "c" * 40


def planner():
    source = Path(__file__).with_name("lab_queue_evidence.py")
    if not source.exists():
        return lambda *args: dict(action="hold", reason="manual-no-normalized-proof")
    spec = importlib.util.spec_from_file_location("lab_evidence_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.plan_from_github


class LabQueueEvidenceAcceptanceTest(unittest.TestCase):
    def setUp(self):
        repo = dict(id=123, full_name=REPO, private=True)
        branch = lambda sha, ref: dict(sha=sha, ref=ref, repo=copy.deepcopy(repo))
        self.repository = repo
        self.main = dict(ref="refs/heads/main", object=dict(sha=BASE))
        self.queue = dict(present=True, numbers=[14], heads={14: HEAD})
        self.pr = dict(number=14, state="open", draft=False, merged=False,
                       head=branch(HEAD, "synthetic-topic"), base=branch(BASE, "main"))
        association = dict(number=14, head=branch(HEAD, "synthetic-topic"),
                           base=branch(BASE, "main"))
        self.runs = [dict(id=100, run_attempt=1, event="pull_request",
                          path=".github/workflows/lab-pr-ci.yml", repository=copy.deepcopy(repo),
                          head_sha=HEAD, status="completed", conclusion="success",
                          created_at="2026-01-01T00:00:00Z", pull_requests=[association])]
        self.jobs = {(100, 1): [dict(name="Lab exact-head verification", conclusion="success",
                                    head_sha=HEAD, runner_group_name="GitHub Actions",
                                    labels=["ubuntu-24.04"])]}
        receipt = dict(head_sha=HEAD, observed_head=HEAD, base_sha=BASE, run_id=100,
                       run_attempt=1, ready=True, lanes={
                           lane: dict(applicability="required", proof="passed")
                           for lane in ("light", "secret", "shadow", "frontend", "backend")})
        self.artifacts = {(100, 1): [dict(name=f"lab-pr-14-{HEAD}-1", expired=False, receipt=receipt)]}
        reviewers = {"首轮": "codex-首轮-one-design", "终审": "codex-终审-one-dev-identity"}
        self.trusted = dict(workflow_verified=True, held=False, reviewers=reviewers,
                            required_reviews=["首轮", "终审"])
        self.reviews = [dict(commit_id=HEAD, state="APPROVED", body=(
            f"复审结论：通过\ncommit：{HEAD}\n复审者：{reviewer}\n\nSynthetic review."))
            for reviewer in reviewers.values()]

    def bundle(self):
        return copy.deepcopy([self.repository, self.main, self.queue, self.pr,
                              self.runs, self.jobs, self.artifacts, self.reviews, self.trusted])

    def decision(self, bundle):
        before = copy.deepcopy(bundle)
        answer = planner()(*bundle)
        self.assertEqual(before, bundle, "normalization must be pure")
        self.assertIsInstance(answer, dict)
        self.assertTrue(answer["reason"])
        return answer

    def test_exact_evidence_is_eligible_with_stable_identity(self):
        result = self.decision(self.bundle())
        self.assertEqual("eligible", result["action"])
        self.assertTrue(result["release_key"])
        self.assertEqual(result["release_key"], self.decision(self.bundle())["release_key"])

    def test_no_canonical_admission_holds_despite_green_and_reviews(self):
        for queue in (dict(present=False, numbers=[14], heads={14: HEAD}),
                      dict(present=True, numbers=[], heads={14: HEAD}),
                      dict(present=True, numbers=[14], heads={}),
                      dict(present=True, numbers=[14], heads={14: OLD})):
            with self.subTest(queue=queue):
                bundle = self.bundle()
                bundle[2] = queue
                self.assertEqual("hold", self.decision(bundle)["action"])

    def test_old_head_or_base_evidence_holds(self):
        for kind in ("main", "head", "association-base", "association-head", "receipt-base"):
            with self.subTest(kind=kind):
                bundle = self.bundle()
                if kind == "main":
                    bundle[1]["object"]["sha"] = OLD
                elif kind == "head":
                    bundle[3]["head"]["sha"] = OLD
                elif kind.startswith("association-"):
                    bundle[4][0]["pull_requests"][0][kind.split("-")[1]]["sha"] = OLD
                else:
                    bundle[6][(100, 1)][0]["receipt"]["base_sha"] = OLD
                self.assertEqual("hold", self.decision(bundle)["action"])

    def test_newer_pending_or_red_run_never_falls_back_to_green(self):
        for run_id, attempt in ((101, 1), (100, 2)):
            for status, conclusion in (("in_progress", None), ("completed", "failure")):
                with self.subTest(run_id=run_id, attempt=attempt, status=status):
                    bundle = self.bundle()
                    newer = copy.deepcopy(bundle[4][0])
                    newer.update(id=run_id, run_attempt=attempt, status=status,
                                 conclusion=conclusion, created_at="2026-01-02T00:00:00Z")
                    # Newest first deliberately: selection must not depend on list order.
                    bundle[4].insert(0, newer)
                    bundle[5][(run_id, attempt)] = copy.deepcopy(bundle[5][(100, 1)])
                    artifact = copy.deepcopy(bundle[6][(100, 1)][0])
                    artifact["name"] = f"lab-pr-14-{HEAD}-{attempt}"
                    artifact["receipt"].update(run_id=run_id, run_attempt=attempt)
                    bundle[6][(run_id, attempt)] = [artifact]
                    self.assertEqual("hold", self.decision(bundle)["action"])

    def test_wrong_runner_or_ambiguous_receipt_holds(self):
        for mutation in ("runner", "labels", "duplicate-job", "duplicate-artifact", "expired"):
            with self.subTest(mutation=mutation):
                bundle = self.bundle()
                if mutation == "runner":
                    bundle[5][(100, 1)][0]["runner_group_name"] = "self-hosted"
                elif mutation == "labels":
                    bundle[5][(100, 1)][0]["labels"] = ["ubuntu-24.04", "self-hosted"]
                elif mutation == "duplicate-job":
                    bundle[5][(100, 1)].append(copy.deepcopy(bundle[5][(100, 1)][0]))
                elif mutation == "duplicate-artifact":
                    bundle[6][(100, 1)].append(copy.deepcopy(bundle[6][(100, 1)][0]))
                else:
                    bundle[6][(100, 1)][0]["expired"] = True
                self.assertEqual("hold", self.decision(bundle)["action"])

    def test_unassigned_dismissed_or_inexact_reviews_cannot_authorize(self):
        for mutation in ("unassigned", "dismissed", "api-commit", "declared-head", "shared-session"):
            with self.subTest(mutation=mutation):
                bundle = self.bundle()
                if mutation == "unassigned":
                    bundle[7][1]["body"] = bundle[7][1]["body"].replace("one-dev-identity", "unassigned")
                elif mutation == "dismissed":
                    dismissed = copy.deepcopy(bundle[7][1])
                    dismissed["state"] = "DISMISSED"
                    bundle[7].append(dismissed)
                elif mutation == "api-commit":
                    bundle[7][1]["commit_id"] = OLD
                elif mutation == "declared-head":
                    bundle[7][1]["body"] = bundle[7][1]["body"].replace(HEAD, OLD)
                else:
                    bundle[8]["reviewers"]["终审"] = "codex-终审-one-design"
                    bundle[7][1]["body"] = bundle[7][1]["body"].replace("one-dev-identity", "one-design")
                self.assertEqual("hold", self.decision(bundle)["action"])


if __name__ == "__main__":
    unittest.main()
