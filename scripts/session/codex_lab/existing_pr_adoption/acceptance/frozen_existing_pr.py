"""Independent existing-PR adoption acceptance.

Contract:
  existing_pr_adoption.observe(db, scope, remote)
    -> {"action": "adopted", "number": 68}
  Invalid or conflicting evidence raises ValueError.

Only observational registration in adopted_pr is permitted.
No first-pr effect or transport is created.
Existing preserved-update suites remain unchanged.
Author has not executed this suite or operated any actual PR.

Missing-capability baseline:
  4 tests, 4 assertion failures, 0 errors, exit 1.
"""
import copy
import importlib
import importlib.util
import sqlite3
import unittest


class ExistingPRAdoptionAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("existing_pr_adoption") is not None:
            module = importlib.import_module("existing_pr_adoption")
        function = getattr(module, "observe", None)
        self.assertTrue(
            callable(function), "EXISTING_PR_ADOPTION_CAPABILITY_MISSING"
        )
        return function

    def fixture(self):
        db = sqlite3.connect(":memory:", isolation_level=None)
        db.row_factory = sqlite3.Row
        self.addCleanup(db.close)
        db.execute("CREATE TABLE effects(kind TEXT,phase TEXT)")
        db.execute("INSERT INTO effects VALUES('native-turn','observed')")
        scope = dict(
            repo_id=1449415666,
            repository="YeliangDu/forcitis-one-psa-codex-lab",
            card=67,
            number=68,
            head_ref="codex/preserved-card67",
            base_ref="main",
            old_head="a" * 40,
            old_base="b" * 40,
            new_base="c" * 40,
            scope_sha256="d" * 64,
            author_session="11111111-1111-4111-8111-111111111111",
            acceptance_session="22222222-2222-4222-8222-222222222222",
            acceptance_sha256="e" * 64,
        )
        remote = dict(scope, state="open", draft=False)
        return db, scope, remote

    def effects(self, db):
        return [
            tuple(row) for row in db.execute(
                "SELECT kind,phase FROM effects ORDER BY rowid"
            )
        ]

    def adoption_rows(self, db):
        exists = db.execute(
            "SELECT 1 FROM sqlite_master "
            "WHERE type='table' AND name='adopted_pr'"
        ).fetchone()
        if not exists:
            return []
        return [
            tuple(row) for row in db.execute(
                "SELECT * FROM adopted_pr ORDER BY rowid"
            )
        ]

    def observe(self, function, db, scope, remote):
        before = copy.deepcopy((scope, remote))
        effects = self.effects(db)
        result = function(db, scope, remote)
        self.assertEqual((scope, remote), before, "INPUT_MUTATED")
        self.assertEqual(self.effects(db), effects, "EFFECTS_CHANGED")
        return result

    def reject(self, function, db, scope, remote):
        before = copy.deepcopy((scope, remote))
        effects = self.effects(db)
        adopted = self.adoption_rows(db)
        with self.assertRaises(ValueError):
            function(db, scope, remote)
        self.assertEqual((scope, remote), before, "INPUT_MUTATED")
        self.assertEqual(self.effects(db), effects, "EFFECTS_CHANGED")
        self.assertEqual(self.adoption_rows(db), adopted,
                         "REJECTED_OBSERVATION_REGISTERED")

    def test_01_exact_observation_registers_once_without_first_pr(self):
        function = self.seam()
        db, scope, remote = self.fixture()
        expected = dict(action="adopted", number=68)
        self.assertEqual(
            self.observe(function, db, scope, remote), expected
        )
        first = self.adoption_rows(db)
        self.assertEqual(len(first), 1)
        self.assertEqual(
            self.observe(function, db, scope, remote), expected
        )
        self.assertEqual(self.adoption_rows(db), first)
        self.assertEqual(
            self.effects(db), [("native-turn", "observed")]
        )

    def test_02_every_scope_field_must_match_observed_remote(self):
        function = self.seam()
        _, template, _ = self.fixture()
        for field in template:
            with self.subTest(field=field):
                db, scope, remote = self.fixture()
                remote[field] = (
                    scope[field] + 1
                    if isinstance(scope[field], int) else "wrong"
                )
                self.reject(function, db, scope, remote)

        for changes in (
            dict(state="closed"),
            dict(draft=True),
        ):
            with self.subTest(changes=changes):
                db, scope, remote = self.fixture()
                remote.update(changes)
                self.reject(function, db, scope, remote)

    def test_03_missing_or_unresolved_identity_and_unknown_effect_rejected(self):
        function = self.seam()
        for field in ("author_session", "acceptance_session"):
            for invalid in (None, "", "not-a-native-id"):
                with self.subTest(field=field, invalid=invalid):
                    db, scope, remote = self.fixture()
                    # Agreement on invalid identity is insufficient.
                    scope[field] = remote[field] = invalid
                    self.reject(function, db, scope, remote)
            db, scope, remote = self.fixture()
            del remote[field]
            self.reject(function, db, scope, remote)

        db, scope, remote = self.fixture()
        db.execute("INSERT INTO effects VALUES('native-turn','unknown')")
        self.reject(function, db, scope, remote)

    def test_04_existing_binding_cannot_be_replaced_by_new_agreement(self):
        function = self.seam()
        mutations = dict(
            number=69,
            card=70,
            old_head="f" * 40,
            head_ref="codex/other",
            author_session="33333333-3333-4333-8333-333333333333",
            acceptance_session="44444444-4444-4444-8444-444444444444",
            acceptance_sha256="f" * 64,
            scope_sha256="1" * 64,
        )
        for field, value in mutations.items():
            with self.subTest(field=field):
                db, scope, remote = self.fixture()
                self.assertEqual(
                    self.observe(function, db, scope, remote),
                    dict(action="adopted", number=68),
                )
                changed_scope = dict(scope, **{field: value})
                changed_remote = dict(remote, **{field: value})
                self.reject(
                    function, db, changed_scope, changed_remote
                )


if __name__ == "__main__":
    unittest.main(verbosity=2)
