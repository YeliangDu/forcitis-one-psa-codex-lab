"""#45 actual controller fence tests, normalized SYNTHETIC port accidents only.

No NativeGithub response is invented, no HTTP exists here. Actual failed-native
permission needs a separately captured open-PR failed attempt with complete proof.
"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest

HERE = Path(__file__).resolve().parent
CORE_SHA = "99122154e3ce796c23e16221e83804df63dcf137ad2c998cfeac4691a40fea8e"


class RerunWindowAcceptanceTest(unittest.TestCase):
    def setUp(self):
        source = HERE / "lab_branch_lifecycle_selftest.py"
        self.assertEqual(CORE_SHA, hashlib.sha256(source.read_bytes()).hexdigest())
        spec = importlib.util.spec_from_file_location("lab45_rerun_frozen_fixture", source)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.core = module.BranchLifecycleAcceptanceTest(methodName="runTest")
        self.core.setUp()
        self.addCleanup(self.core.doCleanups)
        self.core.dispatch()
        self.core.commit_publish("synthetic rerun protocol subject\n")
        self.binding = self.core.command("ensure-pr", dict(binding=self.core.binding))["binding"]
        self.core.github.run = dict(id=700, run_attempt=1, event="pull_request",
            repository=dict(id=module.RID, full_name=module.REPO), head_branch=self.binding["branch"],
            head_sha=self.binding["head"], base_sha=self.binding["base"], pull_request=401,
            path=".github/workflows/lab-pr-ci.yml", status="completed", conclusion="failure")
        self.start = len(self.core.commands)

    def command(self, action="read-ci", **extra):
        result = self.core.command(action, dict(binding=copy.deepcopy(self.binding), **extra))
        print("LAB45_SYNTHETIC_RERUN " + json.dumps(dict(test=self.id(), action=action, result=result,
              state=json.loads(self.core.state.read_text()), normalized_fixture=self.core.github.run,
              rerun_port_calls=self.core.github.reruns, native_evidence=False), sort_keys=True), flush=True)
        self.assertEqual([], self.core.deletes)
        self.assertFalse(any(args[0] in ("push", "merge", "checkout") for args in self.core.commands[self.start:]))
        return result

    def pending(self):
        with self.core.live.life.Store(self.core.state).locked() as store:
            row = store.find(self.binding)
            row["operations"]["rerun"] = dict(status="pending", run_id=700, from_attempt=1, head=self.binding["head"])
            store.save()

    def install_lost(self):
        def lost(run, attempt):
            # Direct read of actual persisted fence; never reacquire its lock.
            row = json.loads(self.core.state.read_text())["bindings"]["45"]
            self.assertEqual("pending", row["operations"]["rerun"]["status"])
            self.assertEqual((700, 1, self.binding["head"]),
                             (row["operations"]["rerun"]["run_id"], row["operations"]["rerun"]["from_attempt"],
                              row["operations"]["rerun"]["head"]))
            self.core.github.reruns += 1
            self.core.github.run.update(run_attempt=2, status="queued", conclusion=None)
            raise TimeoutError("synthetic controller response-loss accident; NOT a native POST")
        self.core.github.rerun = lost

    def test_failed_outcome_cannot_be_verified_ci_and_has_no_rerun_without_explicit_action(self):
        before = self.core.state.read_bytes()
        self.assertEqual("hold", self.command()["action"])
        self.assertEqual(before, self.core.state.read_bytes())
        self.assertEqual(0, self.core.github.reruns)

    def test_pending_fence_precedes_one_synthetic_request_and_lost_reply_only_reconciles(self):
        self.install_lost()
        self.assertEqual("hold", self.command("rerun-ci", run_id=700, run_attempt=1)["action"])
        self.assertEqual(1, self.core.github.reruns)
        self.assertEqual("hold", self.command()["action"], "queued A+1 is not CI success")
        self.core.github.run.update(status="completed", conclusion="success")
        result = self.command()
        self.assertEqual("verified-ci", result["action"])
        self.assertEqual((700, 2), (result["run_id"], result["run_attempt"]))
        self.assertFalse(result["release_authority"])
        self.command("rerun-ci", run_id=700, run_attempt=1)
        self.assertEqual(1, self.core.github.reruns)

    def test_pending_fence_does_not_accept_arbitrary_a_plus_two(self):
        self.pending()
        self.core.github.run.update(run_attempt=3, conclusion="success")
        before = self.core.state.read_bytes()
        result = self.command()
        self.assertEqual("hold", result["action"], "SYNTHETIC_A_PLUS_TWO_ACCEPTED_AS_EXACT_RERUN")
        self.assertEqual(before, self.core.state.read_bytes())
        self.assertEqual(0, self.core.github.reruns)

    def test_pending_same_a_or_changed_run_head_base_is_readback_only(self):
        self.pending()
        original = copy.deepcopy(self.core.github.run)
        for kind in ("same-attempt", "other-run", "head", "base"):
            with self.subTest(kind=kind):
                self.core.github.run = copy.deepcopy(original)
                if kind != "same-attempt":
                    self.core.github.run.update(run_attempt=2, conclusion="success")
                if kind == "other-run":
                    self.core.github.run["id"] = 701
                elif kind == "head":
                    self.core.github.run["head_sha"] = self.binding["base"]
                elif kind == "base":
                    self.core.github.run["base_sha"] = self.binding["head"]
                before = self.core.state.read_bytes()
                self.assertEqual("hold", self.command()["action"])
                self.assertEqual(before, self.core.state.read_bytes())
                self.assertEqual(0, self.core.github.reruns)

    def test_observed_a_plus_one_failure_is_not_verified_ci(self):
        self.pending()
        self.core.github.run.update(run_attempt=2)
        self.assertEqual("hold", self.command()["action"])
        row = json.loads(self.core.state.read_text())["bindings"]["45"]
        self.assertEqual(2, row["operations"]["rerun"]["observed_attempt"])
        self.assertEqual(0, self.core.github.reruns)


if __name__ == "__main__":
    unittest.main(verbosity=2)
