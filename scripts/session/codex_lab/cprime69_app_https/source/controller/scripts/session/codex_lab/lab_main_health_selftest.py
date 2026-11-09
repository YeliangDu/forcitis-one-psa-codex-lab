"""Independent main-health consumption acceptance; fake GitHub port only."""
import copy
import importlib.util
from pathlib import Path
import unittest

HEAD, OLD = "b" * 40, "c" * 40
SLUG = "YeliangDu/forcitis-one-psa-codex-lab"


def checker():
    source = Path(__file__).with_name("lab_main_health.py")
    if not source.exists():
        return lambda github: dict(action="hold", reason="main-health-not-available")
    spec = importlib.util.spec_from_file_location("main_health_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.check_main_health


class GithubFixture:
    def __init__(self, case):
        self.case = case
        self.calls = []
        self.fail = None
        self.repo = dict(id=1449415666, full_name=SLUG, private=False)
        self.main = dict(ref="refs/heads/main", object=dict(sha=HEAD))
        self.final_main = copy.deepcopy(self.main)
        self.runs = [dict(id=100, run_attempt=1, event="push", head_branch="main",
                          path=".github/workflows/lab-pr-ci.yml", repository=copy.deepcopy(self.repo),
                          head_sha=HEAD, status="completed", conclusion="success")]
        self.job_rows = [dict(name="Lab main health", conclusion="success", head_sha=HEAD,
                              runner_group_name="GitHub Actions", labels=["ubuntu-24.04"])]
        self.health = dict(kind="main-health", event="push", head_sha=HEAD, observed_head=HEAD,
                           run_id=100, run_attempt=1, healthy=True,
                           checks=dict(pin="success", light="success", secret="success"))
        self.artifacts = [dict(name=f"lab-main-health-{HEAD}-1", expired=False, health=self.health)]

    def record(self, name, *args):
        self.calls.append((name, *args))
        if self.fail == name:
            raise RuntimeError("synthetic transport error")

    def repository(self):
        self.record("repository")
        return copy.deepcopy(self.repo)

    def main_ref(self):
        self.record("main_ref")
        return copy.deepcopy(self.main if self.calls.count(("main_ref",)) == 1 else self.final_main)

    def main_runs(self, head_sha):
        self.record("main_runs", head_sha)
        self.case.assertEqual(HEAD, head_sha)
        return copy.deepcopy(self.runs)

    def jobs(self, run_id, attempt):
        self.record("jobs", run_id, attempt)
        return copy.deepcopy(self.job_rows)

    def main_artifacts(self, run_id, attempt, head_sha):
        self.record("main_artifacts", run_id, attempt, head_sha)
        return copy.deepcopy(self.artifacts)


class MainHealthAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.github = GithubFixture(self)

    def check(self):
        result = checker()(self.github)
        self.assertIn(result["action"], ("healthy", "hold"))
        self.assertTrue(result["reason"])
        return result

    def test_exact_real_main_health_fields_are_healthy_after_fresh_readback(self):
        result = self.check()
        self.assertEqual("healthy", result["action"])
        self.assertEqual(HEAD, result["head_sha"])
        self.assertEqual(("repository",), self.github.calls[0])
        self.assertEqual(2, self.github.calls.count(("main_ref",)))
        self.assertIn(("jobs", 100, 1), self.github.calls)
        self.assertIn(("main_artifacts", 100, 1, HEAD), self.github.calls)

    def test_missing_stale_or_foreign_main_signal_holds(self):
        for mutation in ("no-runs", "stale-run", "private", "repo-id", "foreign", "wrong-ref", "malformed-sha"):
            with self.subTest(mutation=mutation):
                self.github = GithubFixture(self)
                if mutation == "no-runs":
                    self.github.runs = []
                elif mutation == "stale-run":
                    self.github.runs[0]["head_sha"] = OLD
                elif mutation == "private":
                    self.github.repo["private"] = True
                elif mutation == "repo-id":
                    self.github.repo["id"] = 0
                elif mutation == "foreign":
                    self.github.repo["full_name"] = "synthetic/foreign"
                elif mutation == "wrong-ref":
                    self.github.main["ref"] = "refs/heads/topic"
                else:
                    self.github.main["object"]["sha"] = "bad"
                self.assertEqual("hold", self.check()["action"])

    def test_newest_run_or_attempt_failure_never_falls_back(self):
        for run_id, attempt in ((101, 1), (100, 2)):
            for status, conclusion in (("completed", "failure"), ("in_progress", None)):
                with self.subTest(run_id=run_id, attempt=attempt, status=status):
                    self.github = GithubFixture(self)
                    newer = dict(self.github.runs[0], id=run_id, run_attempt=attempt,
                                 status=status, conclusion=conclusion)
                    self.github.runs.insert(0, newer)
                    self.assertEqual("hold", self.check()["action"])
                    for call in self.github.calls:
                        if call[0] in ("jobs", "main_artifacts"):
                            self.assertEqual((run_id, attempt), call[1:3])

    def test_wrong_run_job_runner_or_ambiguous_artifact_holds(self):
        for mutation in ("event", "workflow", "branch", "repo", "job", "runner", "labels", "job-head", "duplicate-job", "duplicate-artifact", "expired", "missing-artifact"):
            with self.subTest(mutation=mutation):
                self.github = GithubFixture(self)
                run, job = self.github.runs[0], self.github.job_rows[0]
                if mutation == "event": run["event"] = "pull_request"
                elif mutation == "workflow": run["path"] = ".github/workflows/other.yml"
                elif mutation == "branch": run["head_branch"] = "topic"
                elif mutation == "repo": run["repository"]["id"] = 999
                elif mutation == "job": job["name"] = "Lab exact-head verification"
                elif mutation == "runner": job["runner_group_name"] = "self-hosted"
                elif mutation == "labels": job["labels"] = ["ubuntu-24.04", "self-hosted"]
                elif mutation == "job-head": job["head_sha"] = OLD
                elif mutation == "duplicate-job": self.github.job_rows.append(copy.deepcopy(job))
                elif mutation == "duplicate-artifact": self.github.artifacts.append(copy.deepcopy(self.github.artifacts[0]))
                elif mutation == "expired": self.github.artifacts[0]["expired"] = True
                else: self.github.artifacts = []
                self.assertEqual("hold", self.check()["action"])

    def test_malformed_failed_or_inexact_health_proof_holds(self):
        for field, value in (("kind", "pr"), ("event", "pull_request"), ("head_sha", OLD),
                             ("observed_head", OLD), ("run_id", 101), ("run_attempt", 2),
                             ("healthy", False), ("healthy", "true"), ("checks", {}),
                             ("checks", dict(pin="success", light="success", secret="failure")),
                             ("checks", dict(pin="success", light="success", secret="success", extra="success"))):
            with self.subTest(field=field, value=value):
                self.github = GithubFixture(self)
                self.github.health[field] = value
                self.assertEqual("hold", self.check()["action"])
        for field in ("pin", "light", "secret"):
            self.github = GithubFixture(self)
            del self.github.health["checks"][field]
            self.assertEqual("hold", self.check()["action"])

    def test_main_movement_or_transport_failure_holds(self):
        self.github.final_main["object"]["sha"] = OLD
        self.assertEqual("hold", self.check()["action"])
        self.assertEqual(2, self.github.calls.count(("main_ref",)))
        for failure in ("repository", "main_ref", "main_runs", "jobs", "main_artifacts"):
            with self.subTest(failure=failure):
                self.github = GithubFixture(self)
                self.github.fail = failure
                self.assertEqual("hold", self.check()["action"])
                self.assertTrue(any(call[0] == failure for call in self.github.calls))


if __name__ == "__main__":
    unittest.main()
