"""Frozen Lab #8 strict-seam regressions; hosted execution only."""

import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


BASE = "8f08976b8bf886052fbc0a054ca718958758914e"
HEAD = "1" * 40
ZERO_EFFECTS = dict(sends=0, starts=0, merges=0, schedules=0, remote_writes=0)


def invoke(trusted_root, config, snapshot, state_path):
    # Reload each call to discard process-local state. Real import errors propagate.
    spec = importlib.util.spec_from_file_location(
        "lab_shadow_release_target", Path(__file__).with_name("shadow.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    if hasattr(module, "run_lab_tick"):
        return module.run_lab_tick(str(trusted_root), config, snapshot, str(state_path))
    return module.run_tick(config, snapshot, str(state_path))


class ShadowReleaseAcceptanceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        # Resolve the fixture parent once: the trusted root is selected independently.
        self.parent = Path(temporary.name).resolve()
        self.root = self.parent / "lab"
        self.root.mkdir()
        self.worker = self.root / "worker"
        self.worker.mkdir()
        self.control = self.root / ".codex-lab"
        self.control.mkdir()
        self.state = self.control / "release-state.json"
        self.config = dict(repo="YeliangDu/forcitis-one-psa-codex-lab",
                           root=str(self.root), residents={
                               "one-platform": dict(id="synthetic-platform",
                                                    worktree=str(self.worker), available=True)})
        self.card = dict(number=8, role="one-platform", resident_id="synthetic-platform",
                         actual_cwd=str(self.worker), base_sha=BASE, head_sha=HEAD,
                         ci_head=HEAD, ci_ok=True, review_head=HEAD, review_ok=True,
                         hold_reason=None, decision_required=False)
        self.snapshot = dict(repo=self.config["repo"], root=str(self.root),
                             base_sha=BASE, event_id="synthetic-event-1", cards=[self.card])

    def tick(self, config, snapshot, path):
        result = invoke(self.root, copy.deepcopy(config), copy.deepcopy(snapshot), path)
        self.assertEqual(ZERO_EFFECTS, result["effects"])
        return result

    def test_independently_pinned_root_and_state_path_exclusion(self):
        outside = self.parent / "alternate"
        outside.mkdir()
        outside_worker = outside / "worker"
        outside_worker.mkdir()
        outside_control = outside / ".codex-lab"
        outside_control.mkdir()
        forged_config = copy.deepcopy(self.config)
        forged_snapshot = copy.deepcopy(self.snapshot)
        forged_config["root"] = forged_snapshot["root"] = str(outside)
        forged_config["residents"]["one-platform"]["worktree"] = str(outside_worker)
        forged_snapshot["cards"][0]["actual_cwd"] = str(outside_worker)
        link = self.control / "escape"
        link.symlink_to(outside_control, target_is_directory=True)
        alias = self.parent / "lab-alias"
        alias.symlink_to(self.root, target_is_directory=True)
        alias_config = copy.deepcopy(self.config)
        alias_snapshot = copy.deepcopy(self.snapshot)
        alias_config["root"] = alias_snapshot["root"] = str(alias)
        cases = [
            (forged_config, forged_snapshot, outside_control / "forged.json"),
            (self.config, self.snapshot, self.root / "unapproved.json"),
            (self.config, self.snapshot, link / "escaped.json"),
            (alias_config, alias_snapshot, alias / ".codex-lab" / "alias.json"),
        ]
        for config, snapshot, state in cases:
            with self.subTest(state=str(state)):
                result = self.tick(config, snapshot, state)
                self.assertEqual("rejected", result["status"])
                self.assertEqual([], result["decisions"])
                self.assertFalse(state.exists(), "rejection must precede state creation")
                self.assertFalse(Path(str(state) + ".lock").exists(),
                                 "rejection must precede lock creation")

    def test_held_only_event_replay_restart_journal_and_counters(self):
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["cards"][0]["hold_reason"] = "wording-unresolved"
        first = self.tick(self.config, snapshot, self.state)
        self.assertEqual("ok", first["status"])
        self.assertEqual(1, len(first["decisions"]))
        self.assertEqual("hold", first["decisions"][0]["action"])
        self.assertTrue(self.state.exists(), "held-only event must be journaled")
        recorded = json.loads(self.state.read_text())
        self.assertEqual({}, recorded["assignments"])
        self.assertEqual({snapshot["event_id"]}, set(recorded["ticks"]))
        self.assertEqual(first["decisions"],
                         recorded["ticks"][snapshot["event_id"]]["decisions"])
        expected = dict(ticks=1, would_dispatch=0, hold=1, skip=0)
        self.assertEqual(expected, {key: recorded["counters"][key] for key in expected})
        for _ in range(3):
            replay = self.tick(self.config, snapshot, self.state)
            self.assertEqual(first["decisions"], replay["decisions"])
            self.assertEqual(recorded, json.loads(self.state.read_text()))
        changed = copy.deepcopy(snapshot)
        changed["cards"][0]["hold_reason"] = None
        conflict = self.tick(self.config, changed, self.state)
        self.assertTrue(conflict["status"] == "rejected" or (
            len(conflict["decisions"]) == 1 and
            conflict["decisions"][0]["action"] == "hold"))
        self.assertEqual(recorded, json.loads(self.state.read_text()),
                         "conflicting replay cannot overwrite journal or counters")

    def test_missing_or_unresolved_business_decision_holds_with_matching_proof(self):
        cases = [
            {}, {"decision_required": None}, {"decision_required": "false"},
            {"decision_required": 0}, {"decision_required": True},
            {"decision_required": True, "decision_verified": False, "decision_head": HEAD},
            {"decision_required": True, "decision_verified": "true", "decision_head": HEAD},
            {"decision_required": True, "decision_verified": True},
            {"decision_required": True, "decision_verified": True, "decision_head": "2" * 40},
        ]
        for index, fields in enumerate(cases):
            with self.subTest(decision=fields):
                snapshot = copy.deepcopy(self.snapshot)
                snapshot["event_id"] = "decision-%s" % index
                card = snapshot["cards"][0]
                del card["decision_required"]
                card.update(fields)
                state = self.control / ("decision-%s.json" % index)
                result = self.tick(self.config, snapshot, state)
                self.assertEqual("ok", result["status"])
                self.assertEqual(1, len(result["decisions"]))
                decision = result["decisions"][0]
                self.assertEqual(8, decision["number"])
                self.assertEqual("hold", decision["action"])
                self.assertTrue(decision["reason"])
                self.assertFalse(decision.get("assignment_key"))
                if state.exists():
                    self.assertEqual({}, json.loads(state.read_text())["assignments"])


if __name__ == "__main__":
    unittest.main()
