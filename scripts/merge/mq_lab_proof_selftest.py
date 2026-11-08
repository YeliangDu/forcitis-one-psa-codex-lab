"""Live proof wiring acceptance with synthetic GitHub JSON; hosted only."""
import copy
import importlib.util
from pathlib import Path
import unittest

BASE = "a8454ced28ab18fce37d495f21d0daae2fa6ce8d"
HEAD, OTHER = "b" * 40, "c" * 40
SLUG = "YeliangDu/forcitis-one-psa-codex-lab"


def verifier():
    source = Path(__file__).with_name("mq_lab_proof.py")
    if not source.exists():
        return lambda *args: dict(action="hold", reason="no-live-proof")
    spec = importlib.util.spec_from_file_location("mq_proof_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.verify_intent


class Github:
    def __init__(self):
        self.calls = []
        self.error = None
        self.repo = dict(id=1449415666, full_name=SLUG, private=False)
        self.pr = dict(number=15, state="open", draft=False, merged=False,
                       title="ci(lab): 统一个人仓合入队列与准确提交闸门（#14）", body="Closes #14\n\nRefs #14",
                       head=dict(sha=HEAD, ref="lab/14", repo=copy.deepcopy(self.repo)),
                       base=dict(sha=BASE, ref="main", repo=copy.deepcopy(self.repo)))
        self.final_head, self.final_base = HEAD, BASE
        self.health_state = "green"
        self.newer = False
        self.reviewers = {"首轮": "codex-首轮-one-design", "终审": "codex-终审-one-dev-identity"}
        self.review_rows = [dict(commit_id=HEAD, state="COMMENTED", body=(
            f"复审结论：通过\ncommit：{HEAD}\n复审者：{reviewer}\n\nSynthetic independent review."))
            for reviewer in self.reviewers.values()]

    def record(self, name, *args):
        self.calls.append((name, *args))
        if self.error == name: raise OSError("synthetic unavailable evidence")

    def repository(self):
        self.record("repository")
        return copy.deepcopy(self.repo)

    def main_ref(self):
        self.record("main_ref")
        return dict(ref="refs/heads/main", object=dict(sha=BASE if self.calls.count(("main_ref",)) == 1 else self.final_base))

    def pull(self, number):
        self.record("pull", number)
        row = copy.deepcopy(self.pr)
        if self.calls.count(("pull", number)) > 1: row["head"]["sha"] = self.final_head
        return row

    def run(self, run_id, event, head):
        association_repo = dict(id=1449415666, name="forcitis-one-psa-codex-lab", url=f"https://api.github.com/repos/{SLUG}")
        return dict(id=run_id, run_attempt=1, event=event, head_branch="main" if event == "push" else "lab/14",
                    path=".github/workflows/lab-pr-ci.yml", repository=copy.deepcopy(self.repo),
                    head_sha=head, status="completed", conclusion="success", created_at="2026-01-01T00:00:00Z",
                    pull_requests=[dict(number=self.pr["number"], head=dict(sha=HEAD, ref="lab/14", repo=association_repo),
                                        base=dict(sha=BASE, ref="main", repo=association_repo))])

    def runs(self, head):
        self.record("runs", head)
        rows = [self.run(100, "pull_request", HEAD)]
        if self.newer: rows.insert(0, dict(rows[0], id=101, conclusion="failure"))
        return rows

    def main_runs(self, head):
        self.record("main_runs", head)
        if self.health_state == "missing": return []
        row = self.run(200, "push", BASE)
        if self.health_state == "red": row["conclusion"] = "failure"
        return [row]

    def jobs(self, run_id, attempt):
        self.record("jobs", run_id, attempt)
        return [dict(name="Lab main health" if run_id == 200 else "Lab exact-head verification",
                     conclusion="success", head_sha=BASE if run_id == 200 else HEAD,
                     runner_group_name="GitHub Actions", labels=["ubuntu-24.04"])]

    def artifacts(self, run_id, attempt):
        self.record("artifacts", run_id, attempt)
        receipt = dict(head_sha=HEAD, observed_head=HEAD, base_sha=BASE, run_id=run_id,
                       run_attempt=attempt, ready=True, lanes={
                           lane: dict(applicability="required", proof="passed")
                           for lane in ("light", "shadow", "frontend", "backend", "secret")})
        return [dict(name=f"lab-pr-{self.pr['number']}-{HEAD}-{attempt}", expired=False, receipt=receipt)]

    def main_artifacts(self, run_id, attempt, head):
        self.record("main_artifacts", run_id, attempt, head)
        health = dict(kind="main-health", event="push", head_sha=BASE, observed_head=BASE,
                      run_id=run_id, run_attempt=attempt, healthy=True,
                      checks=dict(pin="success", light="success", secret="success"))
        return [dict(name=f"lab-main-health-{BASE}-{attempt}", expired=False, health=health)]

    def reviews(self, number):
        self.record("reviews", number)
        return copy.deepcopy(self.review_rows)


class ReleaseProofAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.github = Github()
        self.intent = dict(number=15, head=HEAD, base=BASE, target="main")
        self.approval = dict(self.intent, repo_id=1449415666, required_reviews=["首轮", "终审"],
                             reviewers=copy.deepcopy(self.github.reviewers), run_id=100, run_attempt=1, mode="normal")
        self.trusted = True
        self.bootstrap = True
        self.bootstrap_calls = []

    def check(self):
        def source(intent): return self.trusted
        def bootstrap(intent, approval):
            self.bootstrap_calls.append((copy.deepcopy(intent), copy.deepcopy(approval)))
            return self.bootstrap
        result = verifier()(copy.deepcopy(self.intent), copy.deepcopy(self.approval), self.github, source, bootstrap)
        self.assertTrue(result["reason"])
        return result

    def test_normal_exact_live_health_pr_reviews_and_final_freshness(self):
        result = self.check()
        self.assertEqual("eligible", result["action"])
        for key, value in self.intent.items(): self.assertEqual(value, result[key])
        self.assertTrue(result["release_key"])
        self.assertGreaterEqual(self.github.calls.count(("pull", 15)), 2)
        self.assertGreaterEqual(self.github.calls.count(("main_ref",)), 2)
        self.assertEqual([], self.bootstrap_calls)
        self.intent.pop("base")
        self.assertEqual(BASE, self.check()["base"])

    def test_latest_run_and_approved_attempt_must_match_no_older_green(self):
        for mutation in ("newer-red", "attempt", "run-id"):
            with self.subTest(mutation=mutation):
                self.setUp()
                if mutation == "newer-red": self.github.newer = True
                elif mutation == "attempt": self.approval["run_attempt"] = 2
                else: self.approval["run_id"] = 99
                self.assertEqual("hold", self.check()["action"])

    def test_title_without_required_card_suffix_holds_despite_linked_body(self):
        self.github.pr["title"] = "ci(lab): 统一个人仓合入队列与准确提交闸门"
        self.assertTrue(self.github.pr["body"].startswith("Closes #14\n"))
        self.assertEqual("hold", self.check()["action"])

    def test_bootstrap_only_fixed_pr_base_missing_health_and_explicit_attestation(self):
        self.github.health_state = "missing"
        self.assertEqual("hold", self.check()["action"])
        self.approval["mode"] = "bootstrap"
        self.assertEqual("eligible", self.check()["action"])
        self.assertTrue(self.bootstrap_calls)
        for mutation in ("red", "wrong-pr", "wrong-base", "denied"):
            with self.subTest(mutation=mutation):
                self.setUp()
                self.approval["mode"] = "bootstrap"
                self.github.health_state = "missing"
                if mutation == "red": self.github.health_state = "red"
                elif mutation == "wrong-pr":
                    self.intent["number"] = self.approval["number"] = self.github.pr["number"] = 16
                elif mutation == "wrong-base": self.approval["base"] = OTHER
                else: self.bootstrap = False
                self.assertEqual("hold", self.check()["action"])

    def test_scope_authority_identity_and_reviews_fail_closed(self):
        for mutation in ("source", "source-nonbool", "foreign", "head", "reviews", "dismissed", "title", "card", "target"):
            with self.subTest(mutation=mutation):
                self.setUp()
                if mutation == "source": self.trusted = False
                elif mutation == "source-nonbool": self.trusted = "true"
                elif mutation == "foreign": self.github.repo["private"] = True
                elif mutation == "head": self.github.pr["head"]["sha"] = OTHER
                elif mutation == "reviews": self.github.review_rows = []
                elif mutation == "dismissed":
                    self.github.review_rows.append(dict(self.github.review_rows[-1], state="DISMISSED"))
                elif mutation == "title": self.github.pr["title"] = "invalid"
                elif mutation == "card": self.github.pr["body"] = "no linked issue"
                else: self.intent["target"] = "topic"
                self.assertEqual("hold", self.check()["action"])

    def test_transport_errors_and_final_main_or_head_change_hold(self):
        for mutation in ("main-moved", "head-moved", "repository", "runs", "jobs", "artifacts", "reviews", "main_runs"):
            with self.subTest(mutation=mutation):
                self.setUp()
                if mutation == "main-moved": self.github.final_base = OTHER
                elif mutation == "head-moved": self.github.final_head = OTHER
                else: self.github.error = mutation
                self.assertEqual("hold", self.check()["action"])
                if mutation == "main-moved": self.assertGreaterEqual(self.github.calls.count(("main_ref",)), 2)
                if mutation == "head-moved": self.assertGreaterEqual(self.github.calls.count(("pull", 15)), 2)


if __name__ == "__main__":
    unittest.main()
