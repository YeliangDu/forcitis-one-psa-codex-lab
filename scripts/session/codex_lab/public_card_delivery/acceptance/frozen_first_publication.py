"""Independent first-publication acceptance.

Author: one-test
Native thread: 01a1b614-934c-7250-a715-5cf09e9c02c2
Native turn: unavailable; not fabricated
Execution: not run by author

Dependencies:
  frozen_interrupted.py (previous frozen actual-Store synthetic fixture)
  runtime_store.py
  first_publication.py (candidate seam)
"""
import copy
import importlib
import importlib.util
import threading
import unittest

from frozen_interrupted import InterruptedAcceptance


SUBJECT = dict(
    repo_id=123,
    repository="synthetic-owner/synthetic-lab",
    card=52,
    revision=3,
    branch="codex/synthetic-52",
    base="b" * 40,
    head="c" * 40,
    title="Synthetic publication",
    body="Synthetic body\n",
)


class Port:
    """Synthetic remote state; records every read and write."""
    def __init__(self):
        self.repository = dict(
            id=SUBJECT["repo_id"],
            full_name=SUBJECT["repository"],
            default_branch="main",
        )
        self.ref_value = None
        self.items = []
        self.complete = True
        self.detail = None
        self.calls = []
        self.lose = set()
        self.fail_before = set()

    def repo(self):
        self.calls.append(("repo",))
        return copy.deepcopy(self.repository)

    def ref(self, branch):
        self.calls.append(("ref", branch))
        return self.ref_value

    def create_ref(self, branch, head):
        self.calls.append(("create_ref", branch, head))
        if "ref" in self.fail_before:
            raise TimeoutError("synthetic uncertain ref issue")
        self.ref_value = head
        if "ref" in self.lose:
            raise TimeoutError("synthetic lost ref reply")

    def pulls(self, branch):
        self.calls.append(("pulls", branch))
        return dict(complete=self.complete, items=copy.deepcopy(self.items))

    def create_pr(self, subject):
        self.calls.append(("create_pr", copy.deepcopy(subject)))
        if "pr" in self.fail_before:
            raise TimeoutError("synthetic uncertain PR issue")
        row = dict(
            number=17,
            head=dict(ref=subject["branch"], sha=subject["head"]),
            base=dict(ref="main", sha=subject["base"]),
            title=subject["title"], body=subject["body"],
            draft=True, state="open",
        )
        self.items = [copy.deepcopy(row)]
        self.detail = copy.deepcopy(row)
        if "pr" in self.lose:
            raise TimeoutError("synthetic lost PR reply")
        return copy.deepcopy(row)

    def pull(self, number):
        self.calls.append(("pull", number))
        return copy.deepcopy(self.detail)

    def writes(self, name):
        return sum(call[0] == name for call in self.calls)


class FirstPublicationAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("first_publication") is not None:
            module = importlib.import_module("first_publication")
        function = getattr(module, "advance", None)
        self.assertTrue(
            callable(function), "FIRST_PUBLICATION_CAPABILITY_MISSING"
        )
        return function

    def fixture(self):
        # Reuse the independently frozen real Store constructor and SQL schema.
        store, auth, _, _ = InterruptedAcceptance.fixture(self)
        return store, auth, Port()

    def advance(self, function, store, auth, port, kind, subject=None):
        subject = copy.deepcopy(SUBJECT if subject is None else subject)
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

    def replacement_store_object(self, store):
        # Discard caller object identity; retain the actual committed SQL state.
        other = type(store).__new__(type(store))
        other.db = store.db
        other.mutex = threading.RLock()
        return other

    def test_01_ref_and_pr_are_separate_committed_effects(self):
        function = self.seam()
        store, auth, port = self.fixture()
        original = self.effects(store)

        self.assertEqual(
            self.advance(function, store, auth, port, "ref")["action"],
            "confirmed",
        )
        self.assertEqual(port.ref_value, SUBJECT["head"])
        self.assertEqual(port.writes("create_ref"), 1)
        self.assertEqual(port.writes("create_pr"), 0)
        ref_effects = self.effects(store)
        self.assertEqual(len(set(ref_effects) - set(original)), 1)
        self.assertFalse(store.db.in_transaction)

        self.assertEqual(
            self.advance(function, store, auth, port, "pr")["action"],
            "confirmed",
        )
        final = self.effects(store)
        added = [final[key] for key in set(final) - set(original)]
        self.assertEqual(len(added), 2)
        self.assertEqual(len({row["operation_key"] for row in added}), 2)
        self.assertTrue(all(row["issue_count"] == 1 for row in added))
        self.assertEqual(port.writes("create_ref"), 1)
        self.assertEqual(port.writes("create_pr"), 1)
        self.assertIn(("pull", 17), port.calls)
        self.assertFalse(store.db.in_transaction)
        for key, row in original.items():
            self.assertEqual(final[key], row)

        other = self.replacement_store_object(store)
        for kind in ("ref", "pr"):
            self.assertEqual(
                self.advance(function, other, auth, port, kind)["action"],
                "confirmed",
            )
        self.assertEqual(port.writes("create_ref"), 1)
        self.assertEqual(port.writes("create_pr"), 1)

    def test_02_existing_ref_without_pr_creates_only_pr(self):
        function = self.seam()
        store, auth, port = self.fixture()
        port.ref_value = SUBJECT["head"]
        self.assertEqual(
            self.advance(function, store, auth, port, "ref")["action"],
            "confirmed",
        )
        self.assertEqual(port.writes("create_ref"), 0)
        self.assertEqual(port.writes("create_pr"), 0)
        self.assertEqual(
            self.advance(function, store, auth, port, "pr")["action"],
            "confirmed",
        )
        self.assertEqual(port.writes("create_ref"), 0)
        self.assertEqual(port.writes("create_pr"), 1)

    def test_03_lost_replies_reconcile_without_reissuing(self):
        function = self.seam()
        for kind in ("ref", "pr"):
            with self.subTest(kind=kind):
                store, auth, port = self.fixture()
                if kind == "pr":
                    port.ref_value = SUBJECT["head"]
                port.lose.add(kind)
                self.advance(function, store, auth, port, kind)
                self.assertFalse(store.db.in_transaction)
                writes = port.writes("create_" + kind)
                self.assertEqual(writes, 1)

                other = self.replacement_store_object(store)
                start = len(port.calls)
                self.assertEqual(
                    self.advance(function, other, auth, port, kind)["action"],
                    "confirmed",
                )
                subsequent = port.calls[start:]
                self.assertTrue(subsequent, "REMOTE_READBACK_MISSING")
                self.assertFalse(any(
                    call[0].startswith("create_") for call in subsequent
                ))
                self.assertEqual(port.writes("create_" + kind), writes)

    def test_04_uncertain_issue_with_no_visible_result_never_resends(self):
        function = self.seam()
        for kind in ("ref", "pr"):
            with self.subTest(kind=kind):
                store, auth, port = self.fixture()
                if kind == "pr":
                    port.ref_value = SUBJECT["head"]
                port.fail_before.add(kind)
                self.assertEqual(
                    self.advance(function, store, auth, port, kind)["action"],
                    "hold",
                )
                self.assertEqual(port.writes("create_" + kind), 1)
                port.fail_before.clear()
                other = self.replacement_store_object(store)
                self.assertEqual(
                    self.advance(function, other, auth, port, kind)["action"],
                    "hold",
                )
                self.assertEqual(port.writes("create_" + kind), 1)

    def test_05_incomplete_ambiguous_or_mismatched_readback_holds(self):
        function = self.seam()
        mutations = (
            "wrong-repository", "wrong-ref", "incomplete",
            "multiple", "head-ref", "head-sha", "base-ref", "base-sha",
            "title", "body", "not-draft", "closed", "detail-mismatch",
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                store, auth, port = self.fixture()
                port.ref_value = SUBJECT["head"]
                # Seed exact synthetic remote evidence without a transport write.
                row = dict(
                    number=17,
                    head=dict(ref=SUBJECT["branch"], sha=SUBJECT["head"]),
                    base=dict(ref="main", sha=SUBJECT["base"]),
                    title=SUBJECT["title"], body=SUBJECT["body"],
                    draft=True, state="open",
                )
                port.items = [copy.deepcopy(row)]
                port.detail = copy.deepcopy(row)
                if mutation == "wrong-repository":
                    port.repository["id"] = 999
                elif mutation == "wrong-ref":
                    port.ref_value = "d" * 40
                elif mutation == "incomplete":
                    port.complete = False
                elif mutation == "multiple":
                    port.items.append(dict(copy.deepcopy(row), number=18))
                elif mutation in ("head-ref", "head-sha",
                                  "base-ref", "base-sha"):
                    side, field = mutation.split("-")
                    port.items[0][side][field] = "wrong"
                elif mutation == "not-draft":
                    port.items[0]["draft"] = False
                elif mutation == "closed":
                    port.items[0]["state"] = "closed"
                elif mutation == "detail-mismatch":
                    port.detail["body"] = "wrong"
                else:
                    port.items[0][mutation] = "wrong"
                self.assertEqual(
                    self.advance(function, store, auth, port, "pr")["action"],
                    "hold",
                )
                self.assertEqual(port.writes("create_ref"), 0)
                self.assertEqual(port.writes("create_pr"), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
