"""Independent follow-up publication acceptance.

Author: one-test
Native thread: 01a1b614-934c-7250-a715-5cf09e9c02c2
Native turn: unavailable; not fabricated
Execution: not run by author
No actual Git/GitHub transport is used.
"""
import copy
import importlib
import importlib.util
import json
import threading
import unittest

from frozen_first_publication import (
    FirstPublicationAcceptance,
    Port,
    SUBJECT as FIRST_SUBJECT,
)


SUBJECT = dict(
    FIRST_SUBJECT,
    expected="a" * 40,
    previous_body="Synthetic previous author body\n",
    number=17,
)


class FollowupPort(Port):
    def __init__(self):
        super().__init__()
        self.ref_value = SUBJECT["expected"]
        self.detail = dict(
            number=SUBJECT["number"],
            head=dict(ref=SUBJECT["branch"], sha=SUBJECT["expected"]),
            base=dict(ref="main", sha=SUBJECT["base"]),
            title=SUBJECT["title"],
            body=SUBJECT["previous_body"],
            draft=True,
            state="open",
        )

    def update_ref(self, branch, expected, head):
        self.calls.append(("update_ref", branch, expected, head))
        if "ref" in self.fail_before:
            raise TimeoutError("synthetic uncertain ref update")
        if self.ref_value != expected:
            raise ValueError("synthetic lease mismatch")
        self.ref_value = head
        # A PR tracking this branch sees the updated head.
        self.detail["head"]["sha"] = head
        if "ref" in self.lose:
            raise TimeoutError("synthetic lost ref update reply")

    def ready_pr(self, number, expected_head, title, body):
        self.calls.append(("ready_pr", number, expected_head, title, body))
        if "pr" in self.fail_before:
            raise TimeoutError("synthetic uncertain PR update")
        if (
            self.detail["number"] != number
            or self.detail["head"]["sha"] != expected_head
        ):
            raise ValueError("synthetic PR lease mismatch")
        self.detail.update(title=title, body=body, draft=False)
        if "pr" in self.lose:
            raise TimeoutError("synthetic lost PR update reply")
        return copy.deepcopy(self.detail)

    def pulls(self, branch):
        raise AssertionError("FOLLOWUP_MUST_USE_FIXED_PR_NUMBER")

    def create_ref(self, branch, head):
        raise AssertionError("FOLLOWUP_MUST_NOT_CREATE_REF")

    def create_pr(self, subject):
        raise AssertionError("FOLLOWUP_MUST_NOT_CREATE_PR")


class CardFollowupAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("card_followup") is not None:
            module = importlib.import_module("card_followup")
        function = getattr(module, "advance", None)
        self.assertTrue(
            callable(function), "CARD_FOLLOWUP_CAPABILITY_MISSING"
        )
        return function

    def fixture(self):
        store, auth, _ = FirstPublicationAcceptance.fixture(self)
        row = store.db.execute(
            "SELECT body FROM tasks WHERE revision=3"
        ).fetchone()
        body = json.loads(row["body"])
        body.update(
            card=SUBJECT["card"],
            A=SUBJECT["expected"],
            B=SUBJECT["base"],
            H=SUBJECT["head"],
        )
        store.db.execute(
            "UPDATE tasks SET body=? WHERE revision=3",
            (json.dumps(body, sort_keys=True, separators=(",", ":")),),
        )
        return store, auth, FollowupPort()

    def advance(self, function, store, auth, port, kind, changes=None):
        subject = dict(SUBJECT, **(changes or {}))
        before = copy.deepcopy(subject)
        result = function(store, auth, subject, port, kind)
        self.assertEqual(subject, before, "SUBJECT_MUTATED")
        self.assertIn(result.get("action"), ("confirmed", "hold"))
        return result

    def effects(self, store):
        return {
            row["effect_id"]: dict(row)
            for row in store.db.execute("SELECT * FROM effects")
        }

    def restarted_object(self, store):
        other = type(store).__new__(type(store))
        other.db = store.db
        other.mutex = threading.RLock()
        return other

    def set_updated_ref(self, port):
        port.ref_value = SUBJECT["head"]
        port.detail["head"]["sha"] = SUBJECT["head"]

    def test_01_exact_lease_then_same_pr_ready_have_distinct_durable_effects(self):
        function = self.seam()
        store, auth, port = self.fixture()
        original = self.effects(store)
        self.assertEqual(
            self.advance(function, store, auth, port, "ref")["action"],
            "confirmed",
        )
        self.assertIn(
            ("update_ref", SUBJECT["branch"],
             SUBJECT["expected"], SUBJECT["head"]),
            port.calls,
        )
        self.assertEqual(port.writes("ready_pr"), 0)
        self.assertEqual(len(set(self.effects(store)) - set(original)), 1)
        self.assertFalse(store.db.in_transaction)

        self.assertEqual(
            self.advance(function, store, auth, port, "pr")["action"],
            "confirmed",
        )
        self.assertIn(
            ("ready_pr", SUBJECT["number"], SUBJECT["head"],
             SUBJECT["title"], SUBJECT["body"]),
            port.calls,
        )
        self.assertEqual(port.detail["body"], SUBJECT["body"])
        self.assertIs(port.detail["draft"], False)
        self.assertEqual(port.detail["head"]["sha"], SUBJECT["head"])
        self.assertEqual(port.detail["base"]["sha"], SUBJECT["base"])
        effects = self.effects(store)
        added = [effects[key] for key in set(effects) - set(original)]
        self.assertEqual(len(added), 2)
        self.assertEqual(len({row["operation_key"] for row in added}), 2)
        self.assertTrue(all(row["issue_count"] == 1 for row in added))
        for key, value in original.items():
            self.assertEqual(effects[key], value)
        self.assertFalse(store.db.in_transaction)

        other = self.restarted_object(store)
        for kind in ("ref", "pr"):
            self.assertEqual(
                self.advance(function, other, auth, port, kind)["action"],
                "confirmed",
            )
        self.assertEqual(port.writes("update_ref"), 1)
        self.assertEqual(port.writes("ready_pr"), 1)

    def test_02_other_ref_never_receives_update(self):
        function = self.seam()
        for ref in (None, "d" * 40):
            with self.subTest(ref=ref):
                store, auth, port = self.fixture()
                port.ref_value = ref
                self.assertEqual(
                    self.advance(function, store, auth, port, "ref")["action"],
                    "hold",
                )
                self.assertEqual(port.writes("update_ref"), 0)
                self.assertEqual(port.writes("ready_pr"), 0)

    def test_03_pr_requires_exact_identity_head_base_and_previous_body(self):
        function = self.seam()
        for mutation in (
            "repository", "ref", "number", "head-ref", "head-sha",
            "base-ref", "base-sha", "body", "closed", "already-ready",
        ):
            with self.subTest(mutation=mutation):
                store, auth, port = self.fixture()
                self.set_updated_ref(port)
                if mutation == "repository":
                    port.repository["id"] = 999
                elif mutation == "ref":
                    port.ref_value = SUBJECT["expected"]
                elif mutation == "number":
                    port.detail["number"] = 18
                elif mutation in (
                    "head-ref", "head-sha", "base-ref", "base-sha"
                ):
                    side, field = mutation.split("-")
                    port.detail[side][field] = "wrong"
                elif mutation == "body":
                    port.detail["body"] = "unapproved author body"
                elif mutation == "closed":
                    port.detail["state"] = "closed"
                else:
                    # Ready with the old body is not the requested final state.
                    port.detail["draft"] = False
                self.assertEqual(
                    self.advance(function, store, auth, port, "pr")["action"],
                    "hold",
                )
                self.assertEqual(port.writes("update_ref"), 0)
                self.assertEqual(port.writes("ready_pr"), 0)

    def test_04_lost_reply_is_reconciled_readonly(self):
        function = self.seam()
        for kind, operation in (("ref", "update_ref"), ("pr", "ready_pr")):
            with self.subTest(kind=kind):
                store, auth, port = self.fixture()
                if kind == "pr":
                    self.set_updated_ref(port)
                port.lose.add(kind)
                self.advance(function, store, auth, port, kind)
                self.assertEqual(port.writes(operation), 1)
                self.assertFalse(store.db.in_transaction)
                start = len(port.calls)
                self.assertEqual(
                    self.advance(
                        function, self.restarted_object(store),
                        auth, port, kind,
                    )["action"],
                    "confirmed",
                )
                subsequent = port.calls[start:]
                self.assertTrue(subsequent, "REMOTE_READBACK_MISSING")
                self.assertFalse(any(
                    call[0] in ("update_ref", "ready_pr")
                    for call in subsequent
                ))
                self.assertEqual(port.writes(operation), 1)

    def test_05_unknown_without_visible_result_never_reissues(self):
        function = self.seam()
        for kind, operation in (("ref", "update_ref"), ("pr", "ready_pr")):
            with self.subTest(kind=kind):
                store, auth, port = self.fixture()
                if kind == "pr":
                    self.set_updated_ref(port)
                port.fail_before.add(kind)
                self.assertEqual(
                    self.advance(function, store, auth, port, kind)["action"],
                    "hold",
                )
                self.assertEqual(port.writes(operation), 1)
                port.fail_before.clear()
                self.assertEqual(
                    self.advance(
                        function, self.restarted_object(store),
                        auth, port, kind,
                    )["action"],
                    "hold",
                )
                self.assertEqual(port.writes(operation), 1)

    def test_06_wrong_owner_or_assignment_cannot_issue(self):
        function = self.seam()
        mutations = (
            "owner-client", "owner-generation", "owner-token",
            "card", "revision", "expected", "base", "head",
        )
        for mutation in mutations:
            for kind in ("ref", "pr"):
                with self.subTest(mutation=mutation, kind=kind):
                    store, auth, port = self.fixture()
                    if kind == "pr":
                        self.set_updated_ref(port)
                    changes = {}
                    if mutation.startswith("owner-"):
                        key = mutation.removeprefix("owner-")
                        auth[key] = 0 if key == "generation" else "wrong"
                    else:
                        changes[mutation] = (
                            999 if mutation in ("card", "revision")
                            else "e" * 40
                        )
                    self.assertEqual(
                        self.advance(
                            function, store, auth, port, kind, changes
                        )["action"],
                        "hold",
                    )
                    self.assertEqual(port.writes("update_ref"), 0)
                    self.assertEqual(port.writes("ready_pr"), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
