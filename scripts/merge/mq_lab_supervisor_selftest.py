"""Issue #21 first slice: pure handoff planning, hosted execution only.

Proposed seam mq_lab_supervisor.plan_handoff(snapshot) -> dict with nonempty
reason and action hold/recover-readonly/refresh. Snapshot is independently
collected evidence, never candidate-supplied authority. A refresh result carries
fetch_argv and ordered steps; it authorizes no I/O by itself. Recovery returns
steps=['readback'] and allows_new_write=False. Refresh steps are exactly fetch,
verify-fetched, verify-ancestry, checkout, start-worker. Execution must revalidate
these gates; this unit seam does not establish transport, lock ownership or
actual process replacement. Issue completion additionally requires two real
hosted disposable-ref releases and interrupted-request recovery evidence.
"""
import copy
import importlib.util
from pathlib import Path
import unittest

OLD, NEW, OTHER = (c * 40 for c in "abc")
REPO = "YeliangDu/forcitis-one-psa-codex-lab"
ORIGIN = "git@github.com:" + REPO + ".git"


def planner():
    path = Path(__file__).with_name("mq_lab_supervisor.py")
    if not path.exists():
        # Existing worker has no handoff; fail observable positive assertions.
        return lambda snapshot: dict(action="hold", reason="worker-pinned-to-loaded-base")
    spec = importlib.util.spec_from_file_location("mq_supervisor_acceptance", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.plan_handoff


class SupervisorHandoffAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.snapshot = dict(
            repository=REPO, loaded_commit=OLD, observed_main=NEW, observed_pr_head="d" * 40,
            journal=dict(number=18, status="confirmed", head="d" * 40, base=OLD,
                         target="main", release_key="synthetic-reservation", merge_sha=NEW),
            health=dict(action="healthy", head_sha=NEW, latest=True),
            control=dict(clean=True, remotes=["origin"], fetch_urls=[ORIGIN], push_urls=[ORIGIN],
                         fetched_sha=NEW, fast_forward=True, merge_ancestor=True),
            process=dict(old_exited=True, runner_lock_held=True, worker_count=0))

    def plan(self, snapshot=None):
        evidence = copy.deepcopy(self.snapshot if snapshot is None else snapshot)
        before = copy.deepcopy(evidence)
        result = planner()(evidence)
        self.assertEqual(before, evidence, "pure plan cannot modify proof or journal")
        self.assertTrue(result["reason"])
        self.assertIn(result["action"], ("hold", "recover-readonly", "refresh"))
        return result

    def test_confirmed_exact_new_main_health_allows_only_pinned_ordered_refresh(self):
        result = self.plan()
        self.assertEqual("refresh", result["action"])
        self.assertEqual(["fetch", "--no-tags", "origin", NEW], result["fetch_argv"])
        self.assertEqual(["fetch", "verify-fetched", "verify-ancestry", "checkout", "start-worker"], result["steps"])
        self.assertEqual(OLD, result["old_commit"])
        self.assertEqual(NEW, result["new_commit"])
        self.assertIs(result["allows_new_write"], False, "handoff never transfers prior approval")

    def test_unknown_reserved_requires_explicit_readonly_recovery_without_refresh(self):
        self.snapshot["journal"]["status"] = "reserved"
        del self.snapshot["journal"]["merge_sha"]
        # Recovery is permitted with old code and absent successor health.
        self.snapshot["health"] = None
        result = self.plan()
        self.assertEqual("recover-readonly", result["action"])
        self.assertEqual(["readback"], result["steps"])
        self.assertIs(result["allows_new_write"], False)
        self.assertNotIn("fetch_argv", result)
        self.assertEqual(self.snapshot["journal"], result["reservation"])

    def test_missing_red_stale_or_unreadable_health_never_refreshes(self):
        for health in (None, {}, dict(action="hold", head_sha=NEW, latest=True),
                       dict(action="healthy", head_sha=OLD, latest=True),
                       dict(action="healthy", head_sha=NEW, latest=False),
                       dict(action="healthy", head_sha=NEW)):
            with self.subTest(health=health):
                self.snapshot["health"] = health
                self.assertEqual("hold", self.plan()["action"])

    def test_journal_and_main_must_bind_exact_loaded_base_and_known_merge(self):
        for mutation in ("missing", "base", "merge", "target", "unconfirmed", "main", "pr-head"):
            with self.subTest(mutation=mutation):
                snapshot = copy.deepcopy(self.snapshot)
                if mutation == "missing": snapshot["journal"] = None
                elif mutation == "base": snapshot["journal"]["base"] = OTHER
                elif mutation == "merge": snapshot["journal"]["merge_sha"] = OTHER
                elif mutation == "target": snapshot["journal"]["target"] = "topic"
                elif mutation == "unconfirmed": snapshot["journal"]["status"] = "unknown"
                elif mutation == "main": snapshot["observed_main"] = OTHER
                else: snapshot["observed_pr_head"] = OTHER
                self.assertEqual("hold", self.plan(snapshot)["action"])

    def test_dirty_wrong_fetch_or_rewritten_ancestry_cannot_execute_successor(self):
        for field, value in (("clean", False), ("fetched_sha", OLD), ("fetched_sha", None),
                             ("fast_forward", False), ("merge_ancestor", False),
                             ("remotes", ["origin", "extra"]), ("fetch_urls", ["synthetic/foreign"]),
                             ("push_urls", ["synthetic/foreign"])):
            with self.subTest(field=field):
                snapshot = copy.deepcopy(self.snapshot)
                snapshot["control"][field] = value
                self.assertEqual("hold", self.plan(snapshot)["action"])

    def test_old_worker_exit_same_lock_and_no_duplicate_are_required(self):
        for field, value in (("old_exited", False), ("runner_lock_held", False), ("worker_count", 1),
                             ("worker_count", 2)):
            with self.subTest(field=field, value=value):
                snapshot = copy.deepcopy(self.snapshot)
                snapshot["process"][field] = value
                self.assertEqual("hold", self.plan(snapshot)["action"])
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["repository"] = "synthetic/foreign"
        self.assertEqual("hold", self.plan(snapshot)["action"])


if __name__ == "__main__":
    unittest.main()
