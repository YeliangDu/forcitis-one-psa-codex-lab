import copy
import hashlib
import importlib
import importlib.util
import json
import sqlite3
import threading
import unittest

THREAD = "11111111-1111-4111-8111-111111111111"
TURN = "22222222-2222-4222-8222-222222222222"
EFFECT = "d" * 48
OWNER = dict(client="synthetic-owner", generation=1, token="synthetic-token")


def encoded(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def rollout(events=None, thread=THREAD):
    if events is None:
        events = [dict(
            type="turn_aborted", turn_id=TURN, reason="interrupted"
        )]
    records = [dict(type="session_meta", payload=dict(id=thread))]
    records.extend(dict(type="event_msg", payload=event) for event in events)
    return ("\n".join(encoded(record) for record in records) + "\n").encode()


class InterruptedAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("role_interrupted") is not None:
            module = importlib.import_module("role_interrupted")
        function = getattr(module, "reconcile_interrupted", None)
        self.assertTrue(
            callable(function), "INTERRUPTED_RECONCILIATION_CAPABILITY_MISSING"
        )
        return function

    def fixture(self):
        Store = importlib.import_module("runtime_store").Store
        store = Store.__new__(Store)
        store.db = sqlite3.connect(":memory:", isolation_level=None)
        store.db.row_factory = sqlite3.Row
        store.mutex = threading.RLock()
        self.addCleanup(store.db.close)
        store.db.executescript("""
        CREATE TABLE owner(
          id INTEGER PRIMARY KEY,generation INTEGER,client TEXT,
          token_sha TEXT,paused INTEGER);
        CREATE TABLE tasks(
          revision INTEGER PRIMARY KEY,body TEXT,phase TEXT,
          capability_epoch INTEGER,fence_seq INTEGER);
        CREATE TABLE turns(
          turn_id TEXT PRIMARY KEY,revision INTEGER,
          owner_generation INTEGER,epoch INTEGER,state TEXT);
        CREATE TABLE effects(
          effect_id TEXT PRIMARY KEY,revision INTEGER,generation INTEGER,
          operation_key TEXT,kind TEXT,target TEXT,phase TEXT,
          issue_count INTEGER,observation TEXT,advances INTEGER);
        CREATE TABLE native_dispatch(
          effect_id TEXT PRIMARY KEY,session_id TEXT,revision INTEGER,
          generation INTEGER,epoch INTEGER,prompt_sha256 TEXT,
          phase TEXT,connection_id TEXT,turn_id TEXT);
        CREATE TABLE waits(wait_id TEXT,revision INTEGER,phase TEXT);
        CREATE TABLE events(
          seq INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT,body TEXT);
        CREATE TABLE role_registration(
          id INTEGER PRIMARY KEY,effect_id TEXT,phase TEXT,
          generation INTEGER,session_id TEXT,first_turn_id TEXT,
          readback_sha256 TEXT);
        CREATE TABLE native_tool_operations(
          session_id TEXT,turn_id TEXT,call_id TEXT,revision INTEGER,
          generation INTEGER,epoch INTEGER,connection_id TEXT,phase TEXT,
          authorization_seq INTEGER,execution_seq INTEGER,result TEXT);
        """)
        auth = copy.deepcopy(OWNER)
        store.db.execute(
            "INSERT INTO owner VALUES(1,1,?,?,1)",
            (auth["client"], digest(auth["token"].encode())),
        )
        for revision in (1, 2, 3):
            task = dict(
                card=1, revision=revision, role="one-readonly-lab",
                session_id=THREAD, physical_cwd="/synthetic",
                task="interrupted acceptance",
                A="a" * 40, B="b" * 40, H="c" * 40,
            )
            store.db.execute(
                "INSERT INTO tasks VALUES(?,?,?,1,NULL)",
                (revision, encoded(task),
                 "active" if revision == 3 else "fenced"),
            )
        store.db.execute(
            "INSERT INTO turns VALUES(?,3,1,1,'active')", (TURN,)
        )
        connection_id = "synthetic-connection"
        prompt_sha256 = digest(b"synthetic-prompt")
        target = dict(
            session_id=THREAD,
            connection_id=connection_id,
            prompt_sha256=prompt_sha256,
        )
        store.db.execute(
            "INSERT INTO effects VALUES(?,3,1,?,'native-turn',?,"
            "'observed',1,NULL,0)",
            (EFFECT, f"native-turn:{THREAD}:3", encoded(target)),
        )
        store.db.execute(
            "INSERT INTO native_dispatch VALUES(?,?,3,1,1,?,"
            "'observed',?,?)",
            (EFFECT, THREAD, prompt_sha256, connection_id, TURN),
        )
        store.db.execute(
            "INSERT INTO role_registration VALUES(1,?,'provisional',1,?,?,NULL)",
            ("e" * 48, THREAD, TURN),
        )
        store.db.execute(
            "INSERT INTO events(kind,body) VALUES('synthetic-history','{}')"
        )
        raw = rollout()
        arguments = dict(
            revision=3, thread_id=THREAD, turn_id=TURN,
            effect_id=EFFECT, rollout_sha256=digest(raw),
        )
        return store, auth, arguments, raw

    def snapshot(self, store):
        tables = [
            row[0] for row in store.db.execute(
                "SELECT name FROM sqlite_master "
                "WHERE type='table' ORDER BY name"
            )
        ]
        return {
            table: [tuple(row) for row in store.db.execute(
                'SELECT * FROM "' + table.replace('"', '""')
                + '" ORDER BY rowid'
            )]
            for table in tables
        }

    def refuse(self, function, store, auth, arguments, raw):
        before = self.snapshot(store)
        with self.assertRaises(ValueError):
            function(store, auth, arguments, raw)
        self.assertEqual(before, self.snapshot(store))

    def test_01_unique_interruption_marks_only_business_failure(self):
        function = self.seam()
        store, auth, arguments, raw = self.fixture()
        before = self.snapshot(store)
        result = function(store, auth, arguments, raw)
        self.assertEqual(result["action"], "interrupted-business-failed")
        self.assertTrue(result.get("receipt"), "DURABLE_RECEIPT_MISSING")
        after = self.snapshot(store)
        for table in (
            "owner", "tasks", "effects", "native_dispatch",
            "role_registration", "native_tool_operations", "waits",
        ):
            self.assertEqual(before[table], after[table], table)
        self.assertEqual(store.db.execute(
            "SELECT state FROM turns WHERE turn_id=?", (TURN,)
        ).fetchone()[0], "terminal")
        self.assertEqual(after["events"][:len(before["events"])],
                         before["events"])
        appended = after["events"][len(before["events"]):]
        self.assertEqual(len(appended), 1)
        self.assertEqual(appended[0][1],
                         "role-native-interrupted-business-failed")
        body = json.loads(appended[0][2])
        for key in (
            "revision", "thread_id", "turn_id", "effect_id", "rollout_sha256"
        ):
            self.assertEqual(body[key], arguments[key])
        self.assertIs(body["business_accepted"], False)

    def test_02_missing_wrong_conflicting_or_duplicate_terminal_refused(self):
        function = self.seam()
        aborted = dict(type="turn_aborted", turn_id=TURN, reason="interrupted")
        vectors = [
            rollout([]),
            rollout([dict(aborted, turn_id="wrong-turn")]),
            rollout([dict(aborted, reason="other")]),
            rollout([aborted, aborted]),
            rollout([aborted, dict(type="task_complete", turn_id=TURN)]),
            rollout(thread="wrong-thread"),
            b"not-json\n",
        ]
        for raw in vectors:
            with self.subTest(raw=raw):
                store, auth, arguments, _ = self.fixture()
                arguments["rollout_sha256"] = digest(raw)
                self.refuse(function, store, auth, arguments, raw)
        store, auth, arguments, raw = self.fixture()
        arguments["rollout_sha256"] = "0" * 64
        self.refuse(function, store, auth, arguments, raw)

    def test_03_current_owner_and_pause_required(self):
        function = self.seam()
        for mutation in ("client", "generation", "token", "unpaused"):
            with self.subTest(mutation=mutation):
                store, auth, arguments, raw = self.fixture()
                if mutation == "unpaused":
                    store.db.execute("UPDATE owner SET paused=0")
                elif mutation == "generation":
                    auth["generation"] = 0
                else:
                    auth[mutation] = "wrong"
                self.refuse(function, store, auth, arguments, raw)

    def test_04_dispatch_effect_identity_and_no_tool_completion_required(self):
        function = self.seam()
        mutations = (
            ("revision", "UPDATE native_dispatch SET revision=2"),
            ("thread", "UPDATE native_dispatch SET session_id='wrong'"),
            ("turn", "UPDATE native_dispatch SET turn_id='wrong'"),
            ("generation", "UPDATE native_dispatch SET generation=0"),
            ("epoch", "UPDATE native_dispatch SET epoch=2"),
            ("issues", "UPDATE effects SET issue_count=2"),
            ("effect-generation", "UPDATE effects SET generation=0"),
            ("effect-revision", "UPDATE effects SET revision=2"),
            ("effect-kind", "UPDATE effects SET kind='other'"),
            ("missing-dispatch", "DELETE FROM native_dispatch"),
            ("missing-effect", "DELETE FROM effects"),
        )
        for name, sql in mutations:
            with self.subTest(mutation=name):
                store, auth, arguments, raw = self.fixture()
                store.db.execute(sql)
                self.refuse(function, store, auth, arguments, raw)

        store, auth, arguments, raw = self.fixture()
        store.db.execute(
            "INSERT INTO native_tool_operations VALUES("
            "?,?, 'synthetic-call',3,1,1,'synthetic-connection',"
            "'completed',NULL,NULL,'{}')", (THREAD, TURN)
        )
        self.refuse(function, store, auth, arguments, raw)

    def test_05_repeat_is_idempotent_and_changed_evidence_cannot_reconcile(self):
        function = self.seam()
        store, auth, arguments, raw = self.fixture()
        first = function(store, auth, arguments, raw)
        before = self.snapshot(store)
        second = function(store, auth, arguments, raw)
        self.assertEqual(second["action"], "interrupted-business-failed")
        self.assertEqual(first["receipt"], second["receipt"])
        self.assertEqual(before, self.snapshot(store))
        altered = raw + b"\n"
        changed = dict(arguments, rollout_sha256=digest(altered))
        self.refuse(function, store, auth, changed, altered)
        for key, value in (
            ("revision", 2), ("thread_id", "wrong-thread"),
            ("turn_id", "wrong-turn"), ("effect_id", "wrong-effect"),
        ):
            with self.subTest(key=key):
                self.refuse(
                    function, store, auth, dict(arguments, **{key: value}), raw
                )


    def test_06_fence_requires_matching_observation_and_is_idempotent(self):
        reconcile = self.seam()
        module = importlib.import_module("role_interrupted")
        fence = getattr(module, "fence_interrupted", None)
        self.assertTrue(callable(fence), "INTERRUPTED_FENCE_CAPABILITY_MISSING")

        store, auth, arguments, raw = self.fixture()
        self.refuse(
            fence, store, auth, dict(arguments, observation_seq=1), raw
        )

        result = reconcile(store, auth, arguments, raw)
        observation_seq = result["observation_seq"]
        self.assertIsInstance(observation_seq, int)
        self.assertNotIsInstance(observation_seq, bool)
        observation = store.db.execute(
            "SELECT kind,body FROM events WHERE seq=?", (observation_seq,)
        ).fetchone()
        self.assertIsNotNone(observation)
        self.assertEqual(
            observation["kind"], "role-native-interrupted-business-failed"
        )
        observation_body = json.loads(observation["body"])
        for key, value in arguments.items():
            self.assertEqual(observation_body[key], value)

        self.refuse(
            fence, store, auth,
            dict(arguments, observation_seq=observation_seq + 1), raw,
        )
        before = self.snapshot(store)
        fence_arguments = dict(arguments, observation_seq=observation_seq)
        first = fence(store, auth, fence_arguments, raw)
        self.assertEqual(first["action"], "interrupted-assignment-fenced")
        fenced_seq = first["fenced_seq"]
        self.assertIsInstance(fenced_seq, int)
        self.assertNotIsInstance(fenced_seq, bool)

        after = self.snapshot(store)
        self.assertEqual(
            after["events"][:len(before["events"])], before["events"]
        )
        for table in (
            "effects", "native_dispatch", "turns",
            "role_registration", "native_tool_operations",
        ):
            self.assertEqual(before[table], after[table], table)
        self.assertEqual(store.db.execute(
            "SELECT issue_count FROM effects WHERE effect_id=?", (EFFECT,)
        ).fetchone()[0], 1)

        for kind in ("assignment-revoking", "execution-fenced"):
            self.assertEqual(store.db.execute(
                "SELECT count(*) FROM events WHERE kind=?", (kind,)
            ).fetchone()[0], 1)
        fenced_event = store.db.execute(
            "SELECT kind FROM events WHERE seq=?", (fenced_seq,)
        ).fetchone()
        self.assertIsNotNone(fenced_event)
        self.assertEqual(fenced_event["kind"], "execution-fenced")
        self.assertEqual(store.db.execute(
            "SELECT phase FROM tasks WHERE revision=3"
        ).fetchone()[0], "fenced")

        second = fence(store, auth, fence_arguments, raw)
        self.assertEqual(second, first)
        self.assertEqual(after, self.snapshot(store))


if __name__ == "__main__":
    unittest.main(verbosity=2)
