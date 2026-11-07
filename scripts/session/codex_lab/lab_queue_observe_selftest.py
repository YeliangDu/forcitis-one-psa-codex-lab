"""Synthetic reader-boundary acceptance; never invokes a real queue or network."""
import copy
from contextlib import contextmanager
import importlib.util
from pathlib import Path
import unittest

HEAD, BASE, OLD = "b" * 40, "a" * 40, "c" * 40
REPO = "YeliangDu/forcitis-one-psa-codex-lab"


def observer():
    source = Path(__file__).with_name("lab_queue_observe.py")
    if not source.exists():
        return lambda *args: dict(action="hold", reason="manual-no-observation")
    spec = importlib.util.spec_from_file_location("lab_observe_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.observe_release


class QueuePort:
    def __init__(self, owner):
        self.owner = owner
        self.members = [14]
        self.head = HEAD
        self.locked = False
        self.entries = self.exits = 0

    @contextmanager
    def merge_lock(self):
        self.owner.assertFalse(self.locked)
        self.entries += 1
        self.locked = True
        try:
            yield
        finally:
            self.locked = False
            self.exits += 1

    def numbers(self):
        self.owner.assertTrue(self.locked)
        return list(self.members)

    def head_of(self, number):
        self.owner.assertTrue(self.locked)
        self.owner.assertEqual(14, number)
        return self.head


class GithubPort:
    def __init__(self, owner, queue):
        self.owner, self.queue = owner, queue
        self.calls = []
        self.fail = None
        self.final_head = HEAD
        self.final_main = BASE
        repo = dict(id=123, full_name=REPO, private=True)
        self.repo = repo
        self.pr = dict(number=14, state="open", draft=False, merged=False,
                       head=dict(sha=HEAD, ref="topic", repo=copy.deepcopy(repo)),
                       base=dict(sha=BASE, ref="main", repo=copy.deepcopy(repo)))
        # Actions association repo differs from pulls API: no full_name.
        association_repo = dict(id=123, name="forcitis-one-psa-codex-lab",
                                url=f"https://api.github.com/repos/{REPO}")
        association = dict(number=14,
                           head=dict(sha=HEAD, ref="topic", repo=association_repo),
                           base=dict(sha=BASE, ref="main", repo=association_repo))
        self.run_rows = [dict(id=100, run_attempt=1, event="pull_request",
                              path=".github/workflows/lab-pr-ci.yml", repository=repo,
                              head_sha=HEAD, status="completed", conclusion="success",
                              created_at="2026-01-01T00:00:00Z", pull_requests=[association])]
        self.job_rows = [dict(name="Lab exact-head verification", conclusion="success",
                              head_sha=HEAD, runner_group_name="GitHub Actions",
                              labels=["ubuntu-24.04"])]
        receipt = dict(head_sha=HEAD, observed_head=HEAD, base_sha=BASE, run_id=100,
                       run_attempt=1, ready=True, lanes={
                           lane: dict(applicability="required", proof="passed")
                           for lane in ("light", "shadow", "frontend", "backend")})
        self.artifact_rows = [dict(name=f"lab-pr-14-{HEAD}-1", expired=False, receipt=receipt)]
        self.review_rows = [dict(commit_id=HEAD, state="COMMENTED", body=(
            f"复审结论：通过\ncommit：{HEAD}\n复审者：codex-{kind}-{suffix}\n\nSynthetic."))
            for kind, suffix in (("首轮", "one-design"), ("终审", "one-dev-identity"))]

    def record(self, name, *args):
        self.owner.assertTrue(self.queue.locked, "every remote read needs shared lock")
        self.calls.append((name, *args))
        if self.fail == name:
            raise RuntimeError("synthetic transport failure")

    def repository(self):
        self.record("repository")
        return copy.deepcopy(self.repo)

    def main_ref(self):
        self.record("main_ref")
        count = sum(call[0] == "main_ref" for call in self.calls)
        return dict(ref="refs/heads/main", object=dict(sha=BASE if count == 1 else self.final_main))

    def pull(self, number):
        self.record("pull", number)
        self.owner.assertEqual(14, number)
        row = copy.deepcopy(self.pr)
        if sum(call[0] == "pull" for call in self.calls) > 1:
            row["head"]["sha"] = self.final_head
        return row

    def runs(self, head_sha):
        self.record("runs", head_sha)
        self.owner.assertEqual(HEAD, head_sha)
        return copy.deepcopy(self.run_rows)

    def jobs(self, run_id, attempt):
        self.record("jobs", run_id, attempt)
        return copy.deepcopy(self.job_rows)

    def artifacts(self, run_id, attempt):
        self.record("artifacts", run_id, attempt)
        return copy.deepcopy(self.artifact_rows)

    def reviews(self, number):
        self.record("reviews", number)
        return copy.deepcopy(self.review_rows)


class LabQueueObserveAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.queue = QueuePort(self)
        self.github = GithubPort(self, self.queue)
        self.trusted = dict(workflow_verified=True, held=False, required_reviews=["首轮", "终审"],
                            reviewers={"首轮": "codex-首轮-one-design",
                                       "终审": "codex-终审-one-dev-identity"})

    def observe(self, queue=True, number=14):
        result = observer()(number, self.queue if queue else None, self.github, self.trusted)
        self.assertFalse(self.queue.locked)
        self.assertEqual(self.queue.entries, self.queue.exits)
        self.assertTrue(result["reason"])
        return result

    def test_exact_api_shapes_eligible_and_all_reads_under_shared_lock(self):
        result = self.observe()
        self.assertEqual("eligible", result["action"])
        self.assertTrue(result["release_key"])
        self.assertEqual(1, self.queue.entries)
        self.assertEqual(("repository",), self.github.calls[0])
        self.assertGreaterEqual(self.github.calls.count(("pull", 14)), 2)
        self.assertGreaterEqual(self.github.calls.count(("main_ref",)), 2)
        self.assertEqual([("jobs", 100, 1)], [c for c in self.github.calls if c[0] == "jobs"])
        self.assertEqual([("artifacts", 100, 1)], [c for c in self.github.calls if c[0] == "artifacts"])

    def test_absent_queue_invalid_item_and_admission_make_no_remote_reads(self):
        self.assertEqual("hold", self.observe(queue=False)["action"])
        self.assertEqual([], self.github.calls)
        for number in (0, -1, "14", None):
            with self.subTest(number=number):
                self.assertEqual("hold", self.observe(number=number)["action"])
                self.assertEqual([], self.github.calls)
        for members, head in (([], HEAD), ([14], None)):
            with self.subTest(members=members, head=head):
                self.queue.members, self.queue.head = members, head
                self.assertEqual("hold", self.observe()["action"])
                self.assertEqual([], self.github.calls)

    def test_stale_queue_head_stops_before_actions(self):
        self.queue.head = OLD
        self.assertEqual("hold", self.observe()["action"])
        self.assertEqual(1, self.queue.entries)
        self.assertFalse(any(c[0] in ("runs", "jobs", "artifacts") for c in self.github.calls))

    def test_repository_validated_before_any_other_remote_read(self):
        self.github.repo["private"] = False
        self.assertEqual("hold", self.observe()["action"])
        self.assertEqual([("repository",)], self.github.calls)

    def test_newer_red_run_never_fetches_old_green_proof(self):
        newer = copy.deepcopy(self.github.run_rows[0])
        newer.update(id=101, conclusion="failure", created_at="2026-01-02T00:00:00Z")
        self.github.run_rows.insert(0, newer)
        self.assertEqual("hold", self.observe()["action"])
        self.assertTrue(any(c[0] == "runs" for c in self.github.calls))
        for call in self.github.calls:
            if call[0] in ("jobs", "artifacts"):
                self.assertEqual((101, 1), call[1:])

    def test_transport_exception_holds_and_releases_lock(self):
        self.github.fail = "runs"
        self.assertEqual("hold", self.observe()["action"])
        self.assertIn(("runs", HEAD), self.github.calls)
        self.assertEqual(1, self.queue.entries)
        self.assertEqual(1, self.queue.exits)

    def test_final_head_or_main_movement_and_read_failure_hold(self):
        for changed in ("head", "main", "read-error"):
            with self.subTest(changed=changed):
                self.github = GithubPort(self, self.queue)
                if changed == "head":
                    self.github.final_head = OLD
                elif changed == "main":
                    self.github.final_main = OLD
                else:
                    original = self.github.pull
                    def fail_final(number):
                        if ("pull", number) in self.github.calls:
                            self.github.record("pull", number)
                            raise RuntimeError("synthetic final read failure")
                        return original(number)
                    self.github.pull = fail_final
                self.assertEqual("hold", self.observe()["action"])
                self.assertGreaterEqual(self.github.calls.count(("pull", 14)), 2)
                if changed == "main":
                    self.assertGreaterEqual(self.github.calls.count(("main_ref",)), 2)


if __name__ == "__main__":
    unittest.main()
