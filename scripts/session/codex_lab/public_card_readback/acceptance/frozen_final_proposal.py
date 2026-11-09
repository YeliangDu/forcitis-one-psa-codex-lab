import copy
import importlib
import importlib.util
import json
import unittest


PROPOSAL = {
    "revision": 7,
    "scope_sha256": "a" * 64,
    "edits": [{
        "path": "fixture/logic.py",
        "before_sha256": "b" * 64,
        "old": "value = 1",
        "new": "value = 2",
    }],
}
FINAL = json.dumps(PROPOSAL, ensure_ascii=False, separators=(",", ":"))


def message(phase, text):
    return dict(type="agentMessage", phase=phase, text=text)


class FinalProposalAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("card_capsule") is not None:
            module = importlib.import_module("card_capsule")
        function = getattr(module, "final_proposal", None)
        self.assertTrue(
            callable(function), "FINAL_PROPOSAL_CAPABILITY_MISSING"
        )
        return function

    def test_01_commentary_and_one_exact_final_are_accepted(self):
        function = self.seam()
        for items in (
            [message("final_answer", FINAL)],
            [
                message("commentary", "Checking the sealed assignment."),
                message("final_answer", FINAL),
            ],
            [
                message("commentary", '{"revision":999,"edits":[]}'),
                message("commentary", "The final proposal follows."),
                message("final_answer", FINAL),
            ],
        ):
            with self.subTest(messages=len(items)):
                before = copy.deepcopy(items)
                self.assertEqual(function(items, FINAL), PROPOSAL)
                self.assertEqual(items, before)

    def test_02_missing_duplicate_or_unknown_phase_is_rejected(self):
        function = self.seam()
        cases = [
            [],
            [message("commentary", FINAL)],
            [message("final_answer", FINAL), message("final_answer", FINAL)],
            [message("analysis", "text"), message("final_answer", FINAL)],
            [message("final", FINAL)],
            [message(None, FINAL)],
            [dict(type="agentMessage", text=FINAL)],
            [dict(type="unknown", phase="final_answer", text=FINAL)],
            [message("commentary", None), message("final_answer", FINAL)],
        ]
        for items in cases:
            with self.subTest(items=items):
                before = copy.deepcopy(items)
                with self.assertRaises(ValueError):
                    function(items, FINAL)
                self.assertEqual(items, before)

    def test_03_exact_rollout_match_and_json_object_are_required(self):
        function = self.seam()
        cases = [
            ([message("final_answer", FINAL)], FINAL + "\n"),
            ([message("final_answer", FINAL + "\n")], FINAL),
            # Semantically equal JSON still fails the byte-for-byte text check.
            ([message("final_answer", FINAL)],
             json.dumps(PROPOSAL, indent=2)),
            ([message("final_answer", FINAL)], None),
            ([message("final_answer", None)], FINAL),
        ]
        for text in ("not JSON", "```json\n" + FINAL + "\n```",
                     "[]", "null", '"text"', '{"revision":'):
            cases.append(([message("final_answer", text)], text))
        for items, last_agent_message in cases:
            with self.subTest(items=items, last=last_agent_message):
                before = copy.deepcopy(items)
                with self.assertRaises(ValueError):
                    function(items, last_agent_message)
                self.assertEqual(items, before)


if __name__ == "__main__":
    unittest.main(verbosity=2)
