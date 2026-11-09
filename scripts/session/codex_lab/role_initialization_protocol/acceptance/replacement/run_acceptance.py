import argparse
import hashlib
import importlib
import importlib.util
import json
import os
from pathlib import Path
import sqlite3
import sys
import threading
import unittest

CFG = None
NATIVE = None
REPLACE = None
STORE = None
OLD = "11111111-1111-4111-8111-111111111111"
NEW = "22222222-2222-4222-8222-222222222222"
TURN = "33333333-3333-4333-8333-333333333333"
EPOCH = "replacement-epoch"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def dump(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


class Acceptance(unittest.TestCase):
    def seam(self, name):
        function = getattr(REPLACE, name, None)
        self.assertTrue(
            callable(function), "ROLE_REPLACEMENT_SEAM_MISSING:" + name
        )
        return function

    def fixture(self):
        store = STORE.__new__(STORE)
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
        auth = dict(client="synthetic-owner", generation=1, token="test-token")
        task = dict(
            card=1, revision=1, role="one-readonly-lab",
            session_id=OLD, physical_cwd=str(CFG.runner_temp),
            task="synthetic replacement acceptance",
            A="a" * 40, B="b" * 40, H="c" * 40,
        )
        store.db.execute(
            "INSERT INTO owner VALUES(1,1,?,?,1)",
            (auth["client"], sha(auth["token"].encode())),
        )
        store.db.execute(
            "INSERT INTO tasks VALUES(1,?,'active',1,NULL)", (dump(task),)
        )
        store.db.execute(
            "INSERT INTO turns VALUES(?,1,1,1,'terminal')", (TURN,)
        )
        store.db.execute(
            "INSERT INTO effects VALUES(?,1,1,?,'native-turn','{}',"
            "'observed',1,'{}',0)",
            ("d" * 48, "synthetic-operation"),
        )
        store.db.execute(
            "INSERT INTO native_dispatch VALUES(?,?,1,1,1,?,"
            "'observed',?,?)",
            ("d" * 48, OLD, sha(b"synthetic-prompt"), EPOCH, TURN),
        )
        store.db.execute(
            "INSERT INTO role_registration VALUES(1,?,'registered',1,?,?,?)",
            ("e" * 48, OLD, TURN, sha(b"synthetic-readback")),
        )
        store.db.execute(
            "INSERT INTO events(kind,body) VALUES(?,?)",
            (
                "single-role-readonly-task-business-failed",
                dump(dict(
                    thread_id=OLD, turn_id=TURN, revision=1,
                    native_status="completed", business_accepted=False,
                    effect_id="d" * 48,
                )),
            ),
        )
        return store, auth

    def snapshot(self, store):
        names = [
            row[0] for row in store.db.execute(
                "SELECT name FROM sqlite_master "
                "WHERE type='table' ORDER BY name"
            )
        ]
        return {
            name: [tuple(row) for row in store.db.execute(
                'SELECT * FROM "' + name.replace('"', '""') + '" ORDER BY rowid'
            )]
            for name in names
        }

    def refused(self, store, function, *args):
        before = self.snapshot(store)
        with self.assertRaises(ValueError):
            function(store, *args)
        self.assertEqual(before, self.snapshot(store))

    def test_01_catalog_collaboration_is_explicitly_disabled(self):
        config = NATIVE.expected_config(CFG.source_root)
        self.assertIs(
            config.get("agents", {}).get("enabled"), False,
            "CATALOG_COLLABORATION_DISABLE_MISSING",
        )
        self.assertIs(
            config.get("features", {}).get("multi_agent_v2"), False,
            "MULTI_AGENT_V2_DISABLE_MISSING",
        )

    def test_02_retirement_archives_fences_and_preserves_native_records(self):
        retire = self.seam("retire_failed_role")
        for invalid in ("owner", "unpaused", "active-turn", "no-failure"):
            with self.subTest(invalid=invalid):
                store, auth = self.fixture()
                if invalid == "owner":
                    auth["generation"] = 0
                elif invalid == "unpaused":
                    store.db.execute("UPDATE owner SET paused=0")
                elif invalid == "active-turn":
                    store.db.execute("UPDATE turns SET state='active'")
                else:
                    store.db.execute("DELETE FROM events")
                self.refused(store, retire, auth, OLD)

        store, auth = self.fixture()
        before = self.snapshot(store)
        registration = dict(store.db.execute(
            "SELECT * FROM role_registration"
        ).fetchone())
        self.assertEqual(retire(store, auth, OLD)["action"], "role-retired")
        after = self.snapshot(store)
        for table in ("effects", "native_dispatch", "turns", "owner"):
            self.assertEqual(before[table], after[table], table)
        self.assertEqual(store.db.execute(
            "SELECT phase FROM role_registration WHERE id=1"
        ).fetchone()[0], "retired")
        task = store.db.execute(
            "SELECT phase,capability_epoch,fence_seq FROM tasks WHERE revision=1"
        ).fetchone()
        self.assertTrue(
            task["phase"] != "active" or task["capability_epoch"] != 1
            or task["fence_seq"] is not None,
            "OLD_ASSIGNMENT_NOT_FENCED",
        )
        archives = [
            json.loads(row[0]) for row in store.db.execute(
                "SELECT body FROM events WHERE kind=? ORDER BY seq",
                ("role-registration-archived",),
            )
        ]
        self.assertEqual(len(archives), 1)
        self.assertEqual(archives[0]["registration"], registration)
        self.assertEqual(len(after["role_registration"]), 1)
        self.refused(store, retire, auth, OLD)

    def test_03_replacement_reservation_and_binding_are_once_only(self):
        retire = self.seam("retire_failed_role")
        reserve = self.seam("reserve_replacement")
        bind = self.seam("bind_replacement")
        store, auth = self.fixture()
        self.refused(store, reserve, auth, OLD, EPOCH)
        retire(store, auth, OLD)
        native_before = {
            table: self.snapshot(store)[table]
            for table in ("effects", "native_dispatch", "turns")
        }
        wrong_owner = dict(auth, generation=0)
        self.refused(store, reserve, wrong_owner, OLD, EPOCH)
        self.refused(store, bind, auth, OLD, EPOCH, NEW)
        before = self.snapshot(store)
        self.assertEqual(
            reserve(store, auth, OLD, EPOCH)["action"], "replacement-reserved"
        )
        self.assertNotEqual(before, self.snapshot(store),
                            "RESERVATION_NOT_DURABLE")
        self.refused(store, reserve, auth, OLD, EPOCH)
        self.refused(store, bind, auth, OLD, "wrong-epoch", NEW)
        self.refused(store, bind, wrong_owner, OLD, EPOCH, NEW)
        self.refused(store, bind, auth, OLD, EPOCH, OLD)
        self.assertEqual(
            bind(store, auth, OLD, EPOCH, NEW)["action"], "replacement-bound"
        )
        self.refused(store, bind, auth, OLD, EPOCH, NEW)
        self.refused(store, reserve, auth, OLD, "another-epoch")
        registration = store.db.execute(
            "SELECT * FROM role_registration"
        ).fetchall()
        self.assertEqual(len(registration), 1)
        self.assertEqual(registration[0]["session_id"], NEW)
        self.assertEqual(store.db.execute(
            "SELECT paused FROM owner WHERE id=1"
        ).fetchone()[0], 1)
        for table, rows in native_before.items():
            self.assertEqual(rows, self.snapshot(store)[table], table)
        self.assertEqual(store.db.execute(
            "SELECT count(*) FROM native_tool_operations"
        ).fetchone()[0], 0)


def main():
    global CFG, NATIVE, REPLACE, STORE
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument("--runner-temp", required=True, type=Path)
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--evidence-dir", required=True, type=Path)
    parser.add_argument("--parent-netns", required=True)
    parser.add_argument("--source-closure-sha256", required=True)
    CFG = parser.parse_args()
    evidence = CFG.evidence_dir.resolve(strict=True)
    if any(evidence.iterdir()):
        raise SystemExit("Evidence directory must be empty")

    def verdict(value, code):
        text = json.dumps(value, indent=2, sort_keys=True) + "\n"
        (evidence / "verdict.json").write_text(text)
        print(text, end="")
        return code

    try:
        CFG.source_root = CFG.source_root.resolve(strict=True)
        CFG.runner_temp = CFG.runner_temp.resolve(strict=True)
        if (
            sys.platform != "linux" or os.geteuid() == 0
            or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted"
            or os.readlink("/proc/self/ns/net") == CFG.parent_netns
        ):
            raise ValueError("isolated hosted runner required")
        raw = (CFG.source_root / "SOURCE-CLOSURE.json").read_bytes()
        if sha(raw) != CFG.source_closure_sha256:
            raise ValueError("source closure pin mismatch")
        for name, digest in json.loads(raw)["files_sha256"].items():
            path = (CFG.source_root / name).resolve(strict=True)
            path.relative_to(CFG.source_root)
            if sha(path.read_bytes()) != digest:
                raise ValueError("source dependency mismatch")
        sys.path.insert(0, str(CFG.source_root))
        NATIVE = importlib.import_module("role_native")
        STORE = importlib.import_module("runtime_store").Store
        # Missing module is a capability assertion, never an ImportError red.
        if importlib.util.find_spec("role_replacement") is not None:
            REPLACE = importlib.import_module("role_replacement")
    except Exception as error:
        return verdict(dict(
            classification="setup-HOLD", tests=0,
            reason=type(error).__name__,
        ), 86)

    result = unittest.TextTestRunner(verbosity=2).run(
        unittest.defaultTestLoader.loadTestsFromTestCase(Acceptance)
    )
    failures = dict(
        (test._testMethodName, trace) for test, trace in result.failures
    )
    expected = (
        result.testsRun == 3 and len(result.failures) == 3
        and not result.errors and not result.skipped
        and "CATALOG_COLLABORATION_DISABLE_MISSING"
        in failures.get(
            "test_01_catalog_collaboration_is_explicitly_disabled", ""
        )
        and all(
            "ROLE_REPLACEMENT_SEAM_MISSING:" in failures.get(name, "")
            for name in (
                "test_02_retirement_archives_fences_and_preserves_native_records",
                "test_03_replacement_reservation_and_binding_are_once_only",
            )
        )
    )
    hold = bool(result.errors or result.skipped or result.testsRun != 3)
    classification = (
        "execution-HOLD" if hold else
        "expected-capability-assertion-red" if expected else
        "bounded-store-regression-passed" if result.wasSuccessful() else
        "assertion-red"
    )
    return verdict(dict(
        classification=classification,
        tests=result.testsRun, failures=len(result.failures),
        errors=len(result.errors), skips=len(result.skipped),
        live_native_route_proven=False,
        thread_start_or_lost_reply_transport_proven=False,
        public_cli_commands_proven=False,
    ), 86 if hold else (0 if result.wasSuccessful() else 1))


if __name__ == "__main__":
    raise SystemExit(main())
