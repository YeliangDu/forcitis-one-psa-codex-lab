"""Independent Lab #8 acceptance. Execute only on isolated hosted CI."""

import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


BASE = "8f08976b8bf886052fbc0a054ca718958758914e"
HEAD = "1" * 40
EFFECTS = dict(sends=0, starts=0, merges=0, schedules=0, remote_writes=0)


def entry():
    """Reload on each tick: only an absent entry file gets the manual baseline."""
    source = Path(__file__).with_name("shadow.py")
    if not source.exists():
        return lambda config, snapshot, state_path: {
            "status": "ok", "decisions": [], "effects": dict(EFFECTS)
        }
    spec = importlib.util.spec_from_file_location("lab_shadow_acceptance_target", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.run_tick


class ShadowAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "lab"
        self.root.mkdir()
        self.worktree = self.root / "worker"
        self.worktree.mkdir()
        self.state = self.root / "state.json"
        self.config = {
            "repo": "YeliangDu/forcitis-one-psa-codex-lab",
            "root": str(self.root),
            "residents": {
                "one-platform": {
                    "id": "synthetic-platform", "worktree": str(self.worktree),
                    "available": True,
                }
            },
        }
        self.card = dict(
            number=8, role="one-platform", resident_id="synthetic-platform",
            actual_cwd=str(self.worktree), base_sha=BASE, head_sha=HEAD,
            ci_head=HEAD, ci_ok=True, review_head=HEAD, review_ok=True,
            hold_reason=None,
        )
        self.snapshot = dict(repo=self.config["repo"], root=str(self.root),
                             base_sha=BASE, cards=[self.card])

    def tick(self, snapshot=None, config=None, state=None):
        result = entry()(config if config is not None else self.config,
                         snapshot if snapshot is not None else self.snapshot,
                         str(state if state is not None else self.state))
        self.assertEqual(EFFECTS, result["effects"])
        return result

    def decisions(self, result, actions):
        self.assertEqual("ok", result["status"])
        rows = result["decisions"]
        self.assertEqual(len(actions), len(rows))
        by_number = {row["number"]: row for row in rows}
        self.assertEqual(set(actions), set(by_number))
        for number, action in actions.items():
            self.assertEqual(action, by_number[number]["action"])
            self.assertIsInstance(by_number[number]["reason"], str)
            self.assertTrue(by_number[number]["reason"].strip())
        return by_number

    def test_duplicate_event_and_restart_keep_one_pending_assignment(self):
        first = self.decisions(self.tick(), {8: "would-dispatch"})[8]
        key = first["assignment_key"]
        self.assertTrue(key)
        persisted = json.loads(self.state.read_text())
        self.assertEqual({key}, set(persisted["assignments"]))
        self.assertEqual("pending", persisted["assignments"][key]["status"])
        for _ in range(3):
            # Fresh module and copied inputs simulate loss of process-local state.
            again = self.decisions(self.tick(copy.deepcopy(self.snapshot),
                                             copy.deepcopy(self.config)), {8: "skip"})[8]
            self.assertEqual(key, again["assignment_key"])
            self.assertEqual(persisted, json.loads(self.state.read_text()))

    def test_changed_base_head_and_failed_or_missing_proof_hold(self):
        mutations = [dict(base_sha="2" * 40), dict(head_sha="3" * 40),
                     dict(ci_head=None), dict(ci_ok=False), dict(ci_ok=None),
                     dict(review_head="4" * 40), dict(review_ok=False),
                     dict(review_head=None)]
        for index, mutation in enumerate(mutations):
            with self.subTest(mutation=mutation):
                snapshot = copy.deepcopy(self.snapshot)
                snapshot["cards"][0].update(mutation)
                state = self.root / ("proof-%s.json" % index)
                rows = self.decisions(self.tick(snapshot, state=state), {8: "hold"})
                self.assertFalse(rows[8].get("assignment_key"))
                if state.exists():
                    self.assertFalse(json.loads(state.read_text()).get("assignments", {}))

    def test_wrong_repository_or_root_rejected_without_state(self):
        for mutation in (dict(repo="synthetic/forbidden"),
                         dict(root=str(Path(self.temp.name)))):
            with self.subTest(mutation=mutation):
                snapshot = copy.deepcopy(self.snapshot)
                snapshot.update(mutation)
                result = self.tick(snapshot)
                self.assertEqual("rejected", result["status"])
                self.assertEqual([], result["decisions"])
                self.assertFalse(self.state.exists())

    def test_symlink_escape_rejected_before_state_access(self):
        outside = Path(self.temp.name) / "outside"
        outside.mkdir()
        sentinel = outside / "state.json"
        sentinel.write_text('{"sentinel": "unchanged"}')
        link = self.root / "escape"
        link.symlink_to(outside, target_is_directory=True)
        result = self.tick(state=link / "state.json")
        self.assertEqual("rejected", result["status"])
        self.assertEqual([], result["decisions"])
        self.assertEqual('{"sentinel": "unchanged"}', sentinel.read_text())
        self.assertEqual({sentinel}, set(outside.iterdir()))

    def test_role_id_cwd_and_unavailable_resident_hold(self):
        cases = [dict(role="unknown-role"), dict(resident_id="unknown-id"),
                 dict(actual_cwd=str(self.root)), dict(actual_cwd=str(self.root / "missing"))]
        escaped = self.root / "linked-worker"
        escaped.symlink_to(Path(self.temp.name), target_is_directory=True)
        cases.append(dict(actual_cwd=str(escaped)))
        for index, mutation in enumerate(cases + [None]):
            with self.subTest(mutation=mutation):
                snapshot = copy.deepcopy(self.snapshot)
                config = copy.deepcopy(self.config)
                if mutation is None:
                    config["residents"]["one-platform"]["available"] = False
                else:
                    snapshot["cards"][0].update(mutation)
                state = self.root / ("identity-%s.json" % index)
                rows = self.decisions(self.tick(snapshot, config, state), {8: "hold"})
                self.assertFalse(rows[8].get("assignment_key"))
                if state.exists():
                    self.assertFalse(json.loads(state.read_text()).get("assignments", {}))

    def test_held_lanes_do_not_block_eligible_lane_and_effects_stay_zero(self):
        config = copy.deepcopy(self.config)
        snapshot = copy.deepcopy(self.snapshot)
        for number, role, reason in ((4, "one-design", "wording"),
                                     (6, "one-dev-identity", "technical-entry")):
            worktree = self.root / role
            worktree.mkdir()
            resident_id = "synthetic-" + role
            config["residents"][role] = dict(id=resident_id, worktree=str(worktree), available=True)
            card = dict(self.card, number=number, role=role, resident_id=resident_id,
                        actual_cwd=str(worktree), hold_reason=reason)
            snapshot["cards"].insert(0, card)
        rows = self.decisions(self.tick(snapshot, config),
                              {4: "hold", 6: "hold", 8: "would-dispatch"})
        key = rows[8]["assignment_key"]
        assignments = json.loads(self.state.read_text())["assignments"]
        self.assertEqual({key}, set(assignments))
        self.assertEqual("pending", assignments[key]["status"])
        self.decisions(self.tick(snapshot, config), {4: "hold", 6: "hold", 8: "skip"})


if __name__ == "__main__":
    unittest.main()
